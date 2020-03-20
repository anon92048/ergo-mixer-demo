package org.ergoplatform.ergomix

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit._
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement

class Util(implicit ctx:BlockchainContext) {
  val addressEncoder = new ErgoAddressEncoder(ctx.getNetworkType.networkPrefix)
  def getAddress(address: String) = addressEncoder.fromString(address).get
}

object ErgoMix {
  val mixAmount = 100000000L // NanoErgs
  val feeAmount = 1500000L // NanoErgs
  def getHash(bytes:Array[Byte]) = scorex.crypto.hash.Blake2b256(bytes)
  val g: GroupElement = CryptoConstants.dlogGroup.generator
}

import org.ergoplatform.ergomix.ErgoMix._

class ErgoMix(ctx:BlockchainContext) {
  val fullMixScriptContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.empty(),
    """{
      |  val c1 = SELF.R4[GroupElement].get
      |  val c2 = SELF.R5[GroupElement].get
      |  proveDlog(c2) ||          // either c2 is g^y
      |  proveDHTuple(c1, c1, c2, c2) // or c2 is c1^x = g^xy
      |}""".stripMargin
  )

  val fullMixScriptErgoTree = fullMixScriptContract.getErgoTree

  val fullMixScriptHash = getHash(fullMixScriptErgoTree.bytes)

  val halfMixContract = ctx.compileContract(
    ConstantsBuilder.create().item(
     "fullMixScriptHash", fullMixScriptHash
    ).build(),
    """{
      |  val g = groupGenerator
      |  val gX = SELF.R4[GroupElement].get
      |
      |  val c1 = OUTPUTS(0).R4[GroupElement].get
      |  val c2 = OUTPUTS(0).R5[GroupElement].get
      |
      |  sigmaProp(OUTPUTS(0).value == SELF.value &&
      |  OUTPUTS(1).value == SELF.value &&
      |  blake2b256(OUTPUTS(0).propositionBytes) == fullMixScriptHash &&
      |  blake2b256(OUTPUTS(1).propositionBytes) == fullMixScriptHash &&
      |  OUTPUTS(1).R4[GroupElement].get == c2 &&
      |  OUTPUTS(1).R5[GroupElement].get == c1 &&
      |  SELF.id == INPUTS(0).id) && {
      |    proveDHTuple(g, gX, c1, c2) ||
      |    proveDHTuple(g, gX, c2, c1)
      |  }
      |}""".stripMargin
  )

  val halfMixScriptHash = getHash(halfMixContract.getErgoTree.bytes)

  /*
  The above provides a very basic mechanism of handling fee.
  The complete solution would combine two approaches:
   1. implement fee using a token as described in advanced ergoscript tutorial
   2. implement a fee emission box that can be exchanged with the tokens when spending a full mix box (as explained in forum post)

  However, here the solution is much simpler. It is a variation of the above idea except that step 1 is skipped
  The fee emission box does not require tokens and can emit fee whenever one of the input boxes in the transaction is a full mix box.

  There should be multiple instances of fee emission box to ensure multiple people can use them in the same transaction.
  There is also the issue of avoiding collisions (when two people try to spend the same fee box)

  * */
  def feeEmissionContract(gZ:GroupElement): ErgoContract = ctx.compileContract(
    ConstantsBuilder.create().item(
      "fullMixScriptHash", fullMixScriptHash
    ).item(
      "gZ", gZ).item(
      "feeAmount", feeAmount
    ).build(),
    /* Fee emission box can only be used in spending a full mix box.
    Spending half-mix box can be done if the other input is a full-mix box (see below)

       We can spend a full mix box in two ways:

        1. fee emission box as input #0  |--> external address or half mix box at output #0
           full mix box as input #1      |    fee box at output #1
                                         |    fee emission box at output #2 (change)

        2. half mix box as input #0      |--> full mix box at output #0
           fee emission box as input #1  |    full mix box at output #1
           full mix box as input #2      |    fee box at output #2
                                         |    fee emission box at output #3 (change)
     */
    """
      |{
      |  val fullMixBoxAtInput = {(i:Int) => blake2b256(INPUTS(i).propositionBytes) == fullMixScriptHash }
      |
      |  val feeEmitBoxAtInput = {(i:Int) => INPUTS(i).id == SELF.id }
      |
      |  val feeEmitBoxAtOutput = {(i:Int) =>
      |    OUTPUTS(i).propositionBytes == SELF.propositionBytes &&
      |    OUTPUTS(i).value == SELF.value - feeAmount
      |  }
      |
      |  sigmaProp(
      |    (fullMixBoxAtInput(1) && feeEmitBoxAtInput(0) && feeEmitBoxAtOutput(2)) ||
      |    (fullMixBoxAtInput(2) && feeEmitBoxAtInput(1) && feeEmitBoxAtOutput(3))
      |  ) || proveDlog(gZ)
      |}
      |""".stripMargin
  )
}
