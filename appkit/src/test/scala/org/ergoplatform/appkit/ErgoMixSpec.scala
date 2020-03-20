package org.ergoplatform.appkit

import java.math.BigInteger
import java.util

import org.ergoplatform.ergomix._
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement
import ErgoMix._

class ErgoMixSpec extends PropSpec with Matchers
  with ScalaCheckDrivenPropertyChecks
  with AppkitTesting
  with HttpClientTesting {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val x = BigInt("187235612876647164378132684712638457631278").bigInteger
  val y = BigInt("340956873409567839086738967389673896738906").bigInteger
  val z = BigInt("904850938509285092854092385490285092845092").bigInteger
  val gZ:GroupElement = g.exp(z)
  val noDls = Array[BigInteger]()
  val noDhts = Array[DHT]()
  val noInBoxIds = Array[String]()
  val changeAddress = "9f5ZKbECVTm25JTRQHDHGM5ehC8tUw5g1fCBQ4aaE792rWBFrjK"
  val dummyTxIdAlice = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809"
  val dummyTxIdBob = "e9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809"

  property("ErgoMix") {

    ergoClient.execute { implicit ctx: BlockchainContext =>
      implicit val ergoMix = new ErgoMix(ctx)

      // basic setup
      // Create instance of Alice and Bob using secrets x and y
      val alice = new AliceImpl(x)
      val bob = new BobImpl(y)

      val txB1 = ctx.newTxBuilder
      val txB2 = ctx.newTxBuilder

      // two dummy input boxes to fund Alice and Bob's transactions whenever needed
      val dummyInputBoxAlice = txB1.outBoxBuilder.value(10000000000000L).contract(ctx.compileContract(ConstantsBuilder.empty(),"{sigmaProp(1 < 2)}")).build().convertToInputWith(dummyTxIdAlice, 0)
      val dummyInputBoxBob = txB2.outBoxBuilder.value(10000000000000L).contract(ctx.compileContract(ConstantsBuilder.empty(),"{sigmaProp(2 < 3)}")).build().convertToInputWith(dummyTxIdBob, 0)

      // the output to be created by spending a full mix box
      val endBox = EndBox(ctx.compileContract(ConstantsBuilder.create().item("carol", gZ).build(),"{proveDlog(carol)}").getErgoTree, Nil, ErgoMix.mixAmount)

      // Step 1. Alice creates a half mix box by spending an external box (dummyInputBoxAlice)
      val halfMixBox:HalfMixBox = alice.createHalfMixBox(Array(dummyInputBoxAlice), 0, changeAddress, noDls, noDhts).getHalfMixBox
      // half mix bo created

      // Step 2. Bob spends above half mix box along with his own external box (dummyInputBoxBob)
      val (fullMixTx:FullMixTx, bit:Boolean) = bob.spendHalfMixBox(halfMixBox, Array(dummyInputBoxBob), 10000, changeAddress, noDls, noDhts)
      // println("(basic) mix Tx "+fullMixTx.tx.toJson(true))

      // Step 3a. Alice spends her full mix box
      val (aliceBox, bobBox) = if (bit) fullMixTx.getFullMixBoxes else fullMixTx.getFullMixBoxes.swap
      val aliceTx = alice.spendFullMixBox(aliceBox, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(basic) Alice Tx "+aliceTx.toJson(true))

      // Step 3b. Bob spends his full mix box
      val bobTx = bob.spendFullMixBox(bobBox, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(basic) Bob Tx "+bobTx.toJson(true))

      ////////////////////////////////////////////////////////////////////////////////////
      // advanced use when the full mix box is spend in a remix (i.e., either creating or consuming a half mix box)
      // Step 3a-a. Alice spends her full mix box as in 3a and additionally creates another half mix box (i.e., plays role of Alice in next round)
      val nextX0 = BigInt(1234567123).bigInteger
      val nextY0 = BigInt(12345678901234L).bigInteger
      val nextAlice0 = new AliceImpl(nextX0)
      val nextBob0 = new BobImpl(nextY0)

      val nextHalfTx0:HalfMixTx = alice.spendFullMixBoxNextAlice(aliceBox, nextX0, 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) "+nextHalfTx0.tx.toJson(true))

      val (nextFullMixTx0, nextBit0) = nextBob0.spendHalfMixBox(nextHalfTx0.getHalfMixBox, Array(dummyInputBoxBob), 0, changeAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) -> FullMix (bob) "+nextFullMixTx0.tx.toJson(true))

      val (nextAliceBox0, nextBobBox0) = if (nextBit0) nextFullMixTx0.getFullMixBoxes else nextFullMixTx0.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx0 = nextAlice0.spendFullMixBox(nextAliceBox0, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx0.toJson(true))

      val nextBobSpendFullMixTx0 = nextBob0.spendFullMixBox(nextBobBox0, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx0.toJson(true))

      ////////////////////////////////////////////////////////////////////////////////////
      val nextX1 = nextX0+BigInt(1).bigInteger
      val nextY1 = nextY0+BigInt(1).bigInteger
      val nextAlice1 = new AliceImpl(nextX1)
      val nextBob1 = new BobImpl(nextY1)

      val nextHalfTx1:HalfMixTx = bob.spendFullMixBoxNextAlice(bobBox, nextX1, 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) "+nextHalfTx1.tx.toJson(true))

      val (nextFullMixTx1, nextBit1) = nextBob1.spendHalfMixBox(nextHalfTx1.getHalfMixBox, Array(dummyInputBoxBob), 0, changeAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) -> FullMix (bob)  "+nextFullMixTx1.tx.toJson(true))

      val (nextAliceBox1, nextBobBox1) = if (nextBit1) nextFullMixTx1.getFullMixBoxes else nextFullMixTx1.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx1 = nextAlice1.spendFullMixBox(nextAliceBox1, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx1.toJson(true))

      val nextBobSpendFullMixTx1 = nextBob1.spendFullMixBox(nextBobBox1, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx1.toJson(true))

      ////////////////////////////////////////////////////////////////////////////////////
      val nextY2 = nextY1+BigInt(1).bigInteger
      val nextBob2 = new BobImpl(nextY2)
      val nextAlice2 = nextAlice1

      val (nextFullTx2:FullMixTx, nextBit2) = alice.spendFullMixBoxNextBob(aliceBox, nextHalfTx1.getHalfMixBox, nextY2, 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-2) FullMix (alice) -> FullMix (bob) "+nextFullTx2.tx.toJson(true))

      val (nextAliceBox2, nextBobBox2) = if (nextBit2) nextFullTx2.getFullMixBoxes else nextFullTx2.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx2 = nextAlice2.spendFullMixBox(nextAliceBox2, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-2) FullMix (alice) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx2.toJson(true))

      val nextBobSpendFullMixTx2 = nextBob2.spendFullMixBox(nextBobBox2, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-2) FullMix (alice) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx2.toJson(true))

      ////////////////////////////////////////////////////////////////////////////////////
      val nextY3 = nextY2+BigInt(1).bigInteger
      val nextBob3 = new BobImpl(nextY3)
      val nextAlice3 = nextAlice1

      val (nextFullTx3:FullMixTx, nextBit3) = bob.spendFullMixBoxNextBob(bobBox, nextHalfTx1.getHalfMixBox, nextY3, 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-3) FullMix (bob) -> FullMix (bob) "+nextFullTx3.tx.toJson(true))

      val (nextAliceBox3, nextBobBox3) = if (nextBit3) nextFullTx3.getFullMixBoxes else nextFullTx3.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx3 = nextAlice3.spendFullMixBox(nextAliceBox3, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-3) FullMix (bob) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx3.toJson(true))

      val nextBobSpendFullMixTx3 = nextBob3.spendFullMixBox(nextBobBox3, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)
      // println("(advanced-3) FullMix (bob) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx3.toJson(true))

      ////////////////////////////////////////////////////////////////////////////////////
      alice.spendFullMixBox(bobBox, Seq(endBox), 0, noInBoxIds, changeAddress, noDls, noDhts)   // this should throw exception but it does not
    }
  }

  property("ErgoMixFee") {

    ergoClient.execute { implicit ctx: BlockchainContext =>
      implicit val ergoMix = new ErgoMix(ctx)
      val carol = new CarolImpl(z)
      val alice = new AliceImpl(x)
      val bob = new BobImpl(y)

      val txB1 = ctx.newTxBuilder
      val txB2 = ctx.newTxBuilder

      // two dummy input boxes to fund Alice and Bob's transactions whenever needed
      val dummyInputBoxAlice = txB1.outBoxBuilder.value(10000000000000L).contract(ctx.compileContract(ConstantsBuilder.empty(),"{sigmaProp(1 < 2)}")).build().convertToInputWith(dummyTxIdAlice, 0)
      val dummyInputBoxBob = txB2.outBoxBuilder.value(10000000000000L).contract(ctx.compileContract(ConstantsBuilder.empty(),"{sigmaProp(2 < 3)}")).build().convertToInputWith(dummyTxIdBob, 0)

      // the output to be created by spending a full mix box
      val endBox = EndBox(ctx.compileContract(ConstantsBuilder.create().item("carol", gZ).build(),"{proveDlog(carol)}").getErgoTree, Nil, mixAmount)

      // create a fee emission box
      val feeEmissionTx = carol.createFeeEmissionBox(10000000000000L, Array(dummyInputBoxAlice), 0, changeAddress, noDls, noDhts)
      val feeEmissionBoxAddress = feeEmissionTx.getFeeEmissionBox.address.toString
      // println(feeEmissionBoxAddress)

      // Step 1. Alice creates a half mix box by spending an external box (dummyInputBoxAlice)
      val halfMixBox:HalfMixBox = alice.createHalfMixBox(Array(dummyInputBoxAlice), 0, changeAddress, noDls, noDhts).getHalfMixBox
      // half mix bo created

      // Step 2. Bob spends above half mix box along with his own external box (dummyInputBoxBob)
      val (fullMixTx:FullMixTx, bit:Boolean) = bob.spendHalfMixBox(halfMixBox, Array(dummyInputBoxBob), 10000, changeAddress, noDls, noDhts)
      // println("(basic) mix Tx "+fullMixTx.tx.toJson(true))

      // Now that full mix box exists, we can use it to spend the fee emission box
      // Step 3a. Alice spends her full mix box
      val (aliceBox, bobBox) = if (bit) fullMixTx.getFullMixBoxes else fullMixTx.getFullMixBoxes.swap
      val aliceTx = alice.spendFullMixBox(aliceBox, Seq(endBox), feeAmount, Array(feeEmissionTx.getFeeEmissionBox.inputBox), feeEmissionBoxAddress, noDls, noDhts)
      // println("(basic) Alice Tx "+aliceTx.toJson(true))
      val feeEmissionBox: InputBox = aliceTx.getOutputsToSpend.get(2)

      // Step 3b. Bob spends his full mix box
      val bobTx = bob.spendFullMixBox(bobBox, Seq(endBox), feeAmount, Array(feeEmissionBox), feeEmissionBoxAddress, noDls, noDhts)
      val newFeeEmissionBox = bobTx.getOutputsToSpend.get(2)
      // println("(basic) Bob Tx "+bobTx.toJson(true))
      // println("(basic) newFeeEmissionBox "+newFeeEmissionBox.toJson(true))

      ////////////////////////////////////////////////////////////////////////////////////
      // advanced use when the full mix box is spend in a remix (i.e., either creating or consuming a half mix box)
      // Step 3a-a. Alice spends her full mix box as in 3a and additionally creates another half mix box (i.e., plays role of Alice in next round)
      val nextX0 = BigInt(1234567123).bigInteger
      val nextY0 = BigInt(12345678901234L).bigInteger
      val nextAlice0 = new AliceImpl(nextX0)
      val nextBob0 = new BobImpl(nextY0)

      val nextHalfTx0:HalfMixTx = alice.spendFullMixBoxNextAlice(aliceBox, nextX0, feeAmount, Array(newFeeEmissionBox), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) "+nextHalfTx0.tx.toJson(true))
      val nextFeeEmissionBox0 = nextHalfTx0.tx.getOutputsToSpend.get(2)

      val (nextFullMixTx0, nextBit0) = nextBob0.spendHalfMixBox(nextHalfTx0.getHalfMixBox, Array(dummyInputBoxBob), 0, changeAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) -> FullMix (bob) "+nextFullMixTx0.tx.toJson(true))

      val (nextAliceBox0, nextBobBox0) = if (nextBit0) nextFullMixTx0.getFullMixBoxes else nextFullMixTx0.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx0 = nextAlice0.spendFullMixBox(nextAliceBox0, Seq(endBox), feeAmount, Array(nextFeeEmissionBox0), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx0.toJson(true))
      val nextFeeEmissionBox00 = nextAliceSpendFullMixTx0.getOutputsToSpend.get(2)

      val nextBobSpendFullMixTx0 = nextBob0.spendFullMixBox(nextBobBox0, Seq(endBox), feeAmount, Array(nextFeeEmissionBox00), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-0) FullMix (alice) -> HalfMix (alice) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx0.toJson(true))
      val nextFeeEmissionBox01 = nextBobSpendFullMixTx0.getOutputsToSpend.get(2)

      ////////////////////////////////////////////////////////////////////////////////////
      val nextX1 = nextX0+BigInt(1).bigInteger
      val nextY1 = nextY0+BigInt(1).bigInteger
      val nextAlice1 = new AliceImpl(nextX1)
      val nextBob1 = new BobImpl(nextY1)

      val nextHalfTx1:HalfMixTx = bob.spendFullMixBoxNextAlice(bobBox, nextX1, feeAmount, Array(nextFeeEmissionBox01), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) "+nextHalfTx1.tx.toJson(true))
      val nextFeeEmissionBox1 = nextHalfTx1.tx.getOutputsToSpend.get(2)

      val (nextFullMixTx1, nextBit1) = nextBob1.spendHalfMixBox(nextHalfTx1.getHalfMixBox, Array(dummyInputBoxBob), 0, changeAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) -> FullMix (bob)  "+nextFullMixTx1.tx.toJson(true))

      val (nextAliceBox1, nextBobBox1) = if (nextBit1) nextFullMixTx1.getFullMixBoxes else nextFullMixTx1.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx1 = nextAlice1.spendFullMixBox(nextAliceBox1, Seq(endBox), feeAmount, Array(nextFeeEmissionBox1), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx1.toJson(true))
      val nextFeeEmissionBox10 = nextAliceSpendFullMixTx1.getOutputsToSpend.get(2)

      val nextBobSpendFullMixTx1 = nextBob1.spendFullMixBox(nextBobBox1, Seq(endBox), feeAmount, Array(nextFeeEmissionBox10), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-1) FullMix (bob) -> HalfMix (alice) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx1.toJson(true))
      val nextFeeEmissionBox11 = nextBobSpendFullMixTx1.getOutputsToSpend.get(2)

      ////////////////////////////////////////////////////////////////////////////////////
      val nextY2 = nextY1+BigInt(1).bigInteger
      val nextBob2 = new BobImpl(nextY2)
      val nextAlice2 = nextAlice1

      val (nextFullTx2:FullMixTx, nextBit2) = alice.spendFullMixBoxNextBob(aliceBox, nextHalfTx1.getHalfMixBox, nextY2, feeAmount, Array(nextFeeEmissionBox11), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-2) FullMix (alice) -> FullMix (bob) "+nextFullTx2.tx.toJson(true))
      val nextFeeEmissionBox2 = nextFullTx2.tx.getOutputsToSpend.get(3)

      val (nextAliceBox2, nextBobBox2) = if (nextBit2) nextFullTx2.getFullMixBoxes else nextFullTx2.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx2 = nextAlice2.spendFullMixBox(nextAliceBox2, Seq(endBox), feeAmount, Array(nextFeeEmissionBox2), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-2) FullMix (alice) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx2.toJson(true))
      val nextFeeEmissionBox20 = nextAliceSpendFullMixTx2.getOutputsToSpend.get(2)

      val nextBobSpendFullMixTx2 = nextBob2.spendFullMixBox(nextBobBox2, Seq(endBox), feeAmount, Array(nextFeeEmissionBox20), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-2) FullMix (alice) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx2.toJson(true))
      val nextFeeEmissionBox21 = nextBobSpendFullMixTx2.getOutputsToSpend.get(2)

      ////////////////////////////////////////////////////////////////////////////////////
      val nextY3 = nextY2+BigInt(1).bigInteger
      val nextBob3 = new BobImpl(nextY3)
      val nextAlice3 = nextAlice1

      val (nextFullTx3:FullMixTx, nextBit3) = bob.spendFullMixBoxNextBob(bobBox, nextHalfTx1.getHalfMixBox, nextY3, feeAmount, Array(nextFeeEmissionBox21), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-3) FullMix (bob) -> FullMix (bob) "+nextFullTx3.tx.toJson(true))
      val nextFeeEmissionBox3 = nextFullTx3.tx.getOutputsToSpend.get(3)

      val (nextAliceBox3, nextBobBox3) = if (nextBit3) nextFullTx3.getFullMixBoxes else nextFullTx3.getFullMixBoxes.swap

      val nextAliceSpendFullMixTx3 = nextAlice3.spendFullMixBox(nextAliceBox3, Seq(endBox), feeAmount, Array(nextFeeEmissionBox3), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-3) FullMix (bob) -> FullMix (bob) -> External (alice) "+nextAliceSpendFullMixTx3.toJson(true))
      val nextFeeEmissionBox30 = nextAliceSpendFullMixTx3.getOutputsToSpend.get(2)

      val nextBobSpendFullMixTx3 = nextBob3.spendFullMixBox(nextBobBox3, Seq(endBox), feeAmount, Array(nextFeeEmissionBox30), feeEmissionBoxAddress, noDls, noDhts)
      // println("(advanced-3) FullMix (bob) -> FullMix (bob) -> External (bob) "+nextBobSpendFullMixTx3.toJson(true))
      val nextFeeEmissionBox31 = nextBobSpendFullMixTx3.getOutputsToSpend.get(2)

    }
  }
}

class BadProverSpec extends PropSpec with Matchers
  with ScalaCheckDrivenPropertyChecks
  with AppkitTesting
  with HttpClientTesting {

  property("BadDHProver") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    val g: GroupElement = CryptoConstants.dlogGroup.generator
    val x = BigInt("187235612876647164378132684712638457631278").bigInteger
    val y = BigInt("340956873409567839086738967389673896738906").bigInteger
    val gX:GroupElement = g.exp(x)
    val gY:GroupElement = g.exp(y)
    val gXY:GroupElement = gY.exp(x)

    ergoClient.execute { ctx: BlockchainContext =>
      implicit val ergoMix = new ErgoMix(ctx)
      val input = ctx.newTxBuilder.outBoxBuilder.registers(
        // ErgoValue.of(gY), ErgoValue.of(gXY) // <--- correct one, (Alice's full mix box)
        ErgoValue.of(gXY), ErgoValue.of(gY) // <--- wrong one, (Bob's full mix box). Alice should not be able to sign.
      ).value(10000).contract(ctx.compileContract(
        ConstantsBuilder.empty(),
        """{
          |  val gY = SELF.R4[GroupElement].get
          |  val gXY = SELF.R5[GroupElement].get
          |  proveDHTuple(gY, gY, gXY, gXY)
          |}""".stripMargin
      )).build().convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val txB = ctx.newTxBuilder()
      val output = txB.outBoxBuilder().value(10000).contract(ctx.compileContract(ConstantsBuilder.empty(),"{sigmaProp(true)}")).build()
      val inputs = new java.util.ArrayList[InputBox]()
      inputs.add(input)

      val one = sigmastate.eval.bigIntegerToBigInt(BigInt(2000).bigInteger)
      val unsigned = txB.boxesToSpend(inputs).outputs(output).build()
      ctx.newProverBuilder().withDHTData(gY, gY, gXY, gXY, x).build().sign(unsigned) // alice signing bob's box. Does not work here but works in ErgoMix spec
    }
  }
}

