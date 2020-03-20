package org.ergoplatform.ergomix

import java.math.BigInteger

import ErgoMix._
import sigmastate.eval._
import org.ergoplatform.appkit.{BlockchainContext, ErgoProver, InputBox}
import special.sigma.GroupElement

import scala.jdk.CollectionConverters._

class CarolImpl(z:BigInteger) (implicit ctx: BlockchainContext) extends Carol {
  override def createFeeEmissionBox(amount: Long, inputBoxes: Array[InputBox], feeAmount: Long, changeAddress: String, additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT]): FeeEmissionTx = {
    implicit  val ergoMix = new ErgoMix(ctx)

    val util = new Util()
    val gZ:GroupElement = g.exp(z)
    val contract = ergoMix.feeEmissionContract(gZ)
    val txB = ctx.newTxBuilder()
    val newBox = txB.outBoxBuilder().value(amount).contract(contract).build()
    val inputs = new java.util.ArrayList[InputBox]()

    inputs.addAll(inputBoxes.toList.asJava)

    val txToSign = txB.boxesToSpend(inputs).outputs(newBox).fee(feeAmount).sendChangeTo(util.getAddress(changeAddress)).build()

    val prover: ErgoProver = additionalDHTuples.foldLeft(
      additionalDlogSecrets.foldLeft(
        ctx.newProverBuilder()
      )(
        (ergoProverBuilder, bigInteger) => ergoProverBuilder.withDLogSecret(bigInteger)
      )
    )(
      (ergoProverBuilder, dh) => ergoProverBuilder.withDHTData(dh.gv, dh.hv, dh.uv, dh.vv, dh.x)
    ).build()

    val tx = prover.sign(txToSign)

    val address = util.addressEncoder.fromProposition(contract.getErgoTree).get

    FeeEmissionTx(tx, gZ, address)
  }
}
