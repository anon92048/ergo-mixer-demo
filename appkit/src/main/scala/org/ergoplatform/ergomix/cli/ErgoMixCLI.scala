//  package org.ergoplatform.ergomix.cli

import java.math.BigInteger

import org.ergoplatform.appkit._
import org.ergoplatform.ergomix._
import special.sigma.GroupElement

private object ErgoMixCLIUtil {
  var optClient:Option[ErgoClient] = None

  implicit def stringToBigInteger(s:String) = BigInt(s).bigInteger

  val emptyArr = Array[String]()

  def usingClient[T](f: BlockchainContext => T): T= {
    optClient.fold(throw new Exception("Set client first")){client =>
      client.execute{ctx =>
        f(ctx)
      }
    }
  }

  case class Arg(key:String, value:String)

  def parseArgs(args:Array[String]): (Seq[Arg], BigInt) = {
    implicit val l: Seq[Arg] = args.sliding(2, 2).toList.collect {
      case Array(key, value) => Arg(key, value)
    }
    val url = try getArg("url") catch {case a:Throwable => defaultUrl}
    val mainNet: Boolean = (try getArg("mainNet") catch {case a:Throwable => "true"}).toBoolean
    val secret = BigInt(getArg("secret"), 10)
    Client.setClient(url, mainNet, None)
    (l, secret)
  }

  def getArgs(key:String)(implicit args:Seq[Arg]):Seq[String] = args.filter(_.key == "--"+key).map(_.value) match {
    case Nil => throw new Exception(s"Argument $key missing")
    case any => any
  }
  def getArg(key:String)(implicit args:Seq[Arg]):String = getArgs(key) match {
    case List(arg) => arg
    case _ => throw new Exception(s"Multiple $key arguments")
  }

  val defaultUrl = "http://88.198.13.202:9053/"
  def getProver(secret:BigInt, isAlice:Boolean)(implicit ctx:BlockchainContext) = if (isAlice) new AliceImpl(secret.bigInteger) else new BobImpl(secret.bigInteger)
  def isAlice(implicit args:Seq[Arg]) = getArg("mode") match{
    case "alice" => true
    case "bob" => false
    case any => throw new Exception(s"Invalid mode $any")
  }
}
import ErgoMixCLIUtil._
/*
Usage
java -cp <jarfile> ProveDlogAddress                          --secret 123 --url http://88.198.13.202:9053/ --mainnet true
java -cp <jarfile> NewFeeEmissionBox                         --secret 123 --amount 1234      --inputBoxId abc --inputBoxId def --changeAddress uvw --dLogSecret 123 --url http://88.198.13.202:9053/ --mainNet true
java -cp <jarfile> AliceEntry                                --secret 123                    --inputBoxId abc --inputBoxId def --changeAddress uvw --dLogSecret 123 --url http://88.198.13.202:9053/ --mainnet true
java -cp <jarfile> BobEntry                                  --secret 123 --halfMixBoxId abc --inputBoxId abc --inputBoxId def --changeAddress uvw --dLogSecret 123 --url http://88.198.13.202:9053/ --mainNet true
java -cp <jarfile> FullMixBoxWithdraw       --mode alice|bob --secret 123 --fullMixBoxId xyz --withdrawAddress uvw --feeEmissionBoxId abc --url http://88.198.13.202:9053/ --mainNet true
java -cp <jarfile> FullMixBoxRemixNextAlice --mode alice|bob --secret 123 --fullMixBoxId xyz --nextSecret 456 --feeEmissionBoxId abc --url http://88.198.13.202:9053/ --mainNet true
java -cp <jarfile> FullMixBoxRemixNextBob   --mode alice|bob --secret 123 --fullMixBoxId xyz --nextSecret 456 --nextHalfMixBoxId uvw --feeEmissionBoxId abc --url http://88.198.13.202:9053/ --mainNet true
*/
object AliceEntry {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val inputBoxIds: Seq[String] = getArgs("inputBoxId")
    val changeAddress = getArg("changeAddress")
    val dLogSecret = getArg("dLogSecret")
    val tx = Alice.createHalfMixBox(secret, inputBoxIds.toArray, changeAddress, dLogSecret)
    println(tx)
  }
}
object BobEntry {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val halfMixBoxId = getArg("halfMixBoxId")
    val inputBoxIds: Seq[String] = getArgs("inputBoxId")
    val changeAddress = getArg("changeAddress")
    val dLogSecret = getArg("dLogSecret")
    val tx = Bob.spendHalfMixBox(secret, halfMixBoxId, inputBoxIds.toArray, changeAddress, dLogSecret)
    println(tx)
  }
}
object FullMixBoxWithdraw {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val fullMixBoxId = getArg("fullMixBoxId")
    val feeEmissionBoxId = getArg("feeEmissionBoxId")
    val withdrawAddress = getArg("withdrawAddress")
    val tx = AliceOrBob.spendFullMixBox(isAlice, secret, fullMixBoxId, withdrawAddress, feeEmissionBoxId)
    println(tx)
  }
}
object FullMixBoxRemixAsAlice {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val fullMixBoxId = getArg("fullMixBoxId")
    val feeEmissionBoxId = getArg("feeEmissionBoxId")
    val nextSecret = BigInt(getArg("nextSecret"), 10)
    val tx = AliceOrBob.spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, feeEmissionBoxId)
    println(tx)
  }
}
object FullMixBoxRemixAsBob {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val fullMixBoxId = getArg("fullMixBoxId")
    val halfMixBoxId = getArg("halfMixBoxId")
    val feeEmissionBoxId = getArg("feeEmissionBoxId")
    val nextSecret = BigInt(getArg("nextSecret"), 10)
    val tx = AliceOrBob.spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, halfMixBoxId, feeEmissionBoxId)
    println(tx)
  }
}
object NewFeeEmissionBox {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val amount: Long = getArg("amount").toLong
    val inputBoxIds: Seq[String] = getArgs("inputBoxId")
    val changeAddress = getArg("changeAddress")
    val dLogSecret = getArg("dLogSecret")
    val tx = Carol.createFeeEmissionBox(secret, amount, inputBoxIds.toArray, changeAddress, dLogSecret)
    println(tx)
  }
}
object ProveDlogAddress {
  def main(args:Array[String]):Unit = {
    val (a, secret) = parseArgs(args)
    println(Carol.getProveDlogAddress(secret))
  }
}

object Client {
  def setClient(url:String, isMainnet:Boolean, optApiKey:Option[String]):Int = {
    val $INFO$ = "Returns the current height of the blockchain"
    val $url$ = "http://88.198.13.202:9053/"
    val $isMainnet$ = "true"
    val netWorkType = if (isMainnet) NetworkType.MAINNET else NetworkType.TESTNET
    val apiKey = optApiKey.fold("")(x => x)
    val client = RestApiErgoClient.create(url, netWorkType, apiKey)
    ErgoMixCLIUtil.optClient = Some(client)
    client.execute(ctx => ctx.getHeight)
  }
}

import ErgoMixCLIUtil._
import org.ergoplatform.ergomix.ErgoMix._
import sigmastate.eval._

object AliceOrBob {
  /*
Play Alice's or Bob's role in spending a full-mix box with secret.
fullMixBoxId is the boxId of the full-mix box to spend.
withdrawAddress is the address where the funds are to be sent.
feeEmissionBoxId is the boxId of input boxes funding the transaction

The method attempts to create a transaction outputting a half-mix box at index 0.
   */
  def spendFullMixBox(isAlice:Boolean, secret:BigInt,
                      fullMixBoxId:String, withdrawAddress:String, feeEmissionBoxId:String):String = {
    usingClient{implicit ctx =>
      val feeEmissionBox = ctx.getBoxesById(feeEmissionBoxId)(0)
      val feeEmissionBoxAddress = new Util().addressEncoder.fromProposition(feeEmissionBox.getErgoTree).get.toString
      spendFullMixBox(isAlice, secret, fullMixBoxId, withdrawAddress, Array(feeEmissionBoxId), ErgoMix.feeAmount, feeEmissionBoxAddress, true)
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret.
fullMixBoxId is the boxId of the full-mix box to spend.
withdrawAddress is the address where the funds are to be sent.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  private def spendFullMixBox(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, withdrawAddress: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, broadCast: Boolean) = {
    usingClient{implicit ctx =>
      val alice_or_bob = getProver(secret, isAlice)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      val endBox = EndBox(new Util().getAddress(withdrawAddress).script, Nil, fullMixBox.getValue)
      val tx: SignedTransaction = alice_or_bob.spendFullMixBox(FullMixBox(fullMixBox), Seq(endBox), feeAmount, inputBoxIds, changeAddress, Array[BigInteger](), Array[DHT]())
      if (broadCast) ctx.sendTransaction(tx)
      tx.toJson(false)
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret to generate a new half-mix box for remixing.
That is, perform the first step of the next round by behaving like Alice (by creating a new half-mix box)

fullMixBoxId is the boxId of the full-mix box to spend.
feeEmissionBoxId is the boxId of input boxes funding the transaction
The method attempts to create a transaction outputting a half-mix box at index 0.
   */
  def spendFullMixBox_RemixAsAlice(isAlice:Boolean, secret:BigInt,
                                   fullMixBoxId:String, nextSecret:BigInt, feeEmissionBoxId:String):String = {
    usingClient{implicit ctx =>
      val feeEmissionBox = ctx.getBoxesById(feeEmissionBoxId)(0)
      val feeEmissionBoxAddress = new Util().addressEncoder.fromProposition(feeEmissionBox.getErgoTree).get.toString

      spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, Array(feeEmissionBoxId), ErgoMix.feeAmount, feeEmissionBoxAddress, true)
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret to generate a new half-mix box for remixing.
That is, perform the first step of the next round by behaving like Alice (by creating a new half-mix box)

fullMixBoxId is the boxId of the full-mix box to spend.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  private def spendFullMixBox_RemixAsAlice(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, broadCast: Boolean) = {
    usingClient{implicit ctx =>
      val alice_or_bob = getProver(secret, isAlice)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      val halfMixTx = alice_or_bob.spendFullMixBoxNextAlice(FullMixBox(fullMixBox), nextSecret.bigInteger, feeAmount, inputBoxIds, changeAddress, Array[BigInteger](), Array[DHT]())
      if (broadCast) ctx.sendTransaction(halfMixTx.tx)
      halfMixTx.tx.toJson(false)
    }
  }
  /*
Play Alice's or Bob's role in spending a full-mix box with secret in another full-mix transaction that spends some half-mix box with this full-mix box.
That is, perform the first step of the next round by behaving like Bob (by creating a two new full-mix boxes)

fullMixBoxId is the boxId of the full-mix box to spend.
feeEmissionBoxId is the boxId of input boxes funding the transaction
The method attempts to create a transaction outputting a half-mix box at index 0.
   */
  def spendFullMixBox_RemixAsBob(isAlice:Boolean, secret:BigInt,
                                fullMixBoxId:String, nextSecret:BigInt, nextHalfMixBoxId:String, feeEmissionBoxId:String):Array[String] = {
    usingClient{implicit ctx =>
      val feeEmissionBox = ctx.getBoxesById(feeEmissionBoxId)(0)
      val feeEmissionBoxAddress = new Util().addressEncoder.fromProposition(feeEmissionBox.getErgoTree).get.toString
      spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, nextHalfMixBoxId, Array(feeEmissionBoxId), feeAmount, feeEmissionBoxAddress, true)
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret in another full-mix transaction that spends some half-mix box with this full-mix box.
That is, perform the first step of the next round by behaving like Bob (by creating a two new full-mix boxes)

fullMixBoxId is the boxId of the full-mix box to spend.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  private def spendFullMixBox_RemixAsBob(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt, nextHalfMixBoxId: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, broadCast: Boolean) = {
    usingClient{implicit ctx =>
      val alice_or_bob:FullMixBoxSpender = if (isAlice) new AliceImpl(secret.bigInteger) else new BobImpl(secret.bigInteger)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      val halfMixBox = ctx.getBoxesById(nextHalfMixBoxId)(0)
      val (fullMixTx, bit) = alice_or_bob.spendFullMixBoxNextBob(FullMixBox(fullMixBox), HalfMixBox(halfMixBox), nextSecret.bigInteger, feeAmount, inputBoxIds, changeAddress, Array[BigInteger](), Array[DHT]())
      if (broadCast) ctx.sendTransaction(fullMixTx.tx)
      Array(fullMixTx.tx.toJson(false), bit.toString)
    }
  }
}

object Alice {
  /*
Play Alice's role in creating a half-mix box with secret x.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require another secret for proveDLog which is supplied in proveDlogSecret.
The method attempts to create a transaction outputting a half-mix box at index 0.
   */
  def createHalfMixBox(x:BigInt, otherInputBoxIds:Array[String], changeAddress:String, otherDlogSecret:String):String = {
    createHalfMixBox(x, otherInputBoxIds, feeAmount, changeAddress, Array(otherDlogSecret), true)
  }

  /*
Play Alice's role in creating a half-mix box with secret x.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  private def createHalfMixBox(x: BigInt, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, proverDlogSecrets: Array[String], broadCast: Boolean) = {
    usingClient{implicit ctx =>
      val alice = new AliceImpl(x.bigInteger)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val tx = alice.createHalfMixBox(inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT]())
      if (broadCast) ctx.sendTransaction(tx.tx)
      tx.tx.toJson(false)
    }
  }
}

object Bob {
  /*
Play Bob's role in creating a full-mix box with secret y.
halfMixBoxId is the boxId of the half-mix box created by an instance of Alice.
inputBoxIds are boxIds of additional input boxes funding the transaction.
Signing may require an additional dLog which is supplied as proveDlogSecret.
The method attempts to create a transaction outputting two full-mix box at index 0 and 1
   */
  def spendHalfMixBox(y:BigInt, halfMixBoxId:String, otherInputBoxIds:Array[String], changeAddress:String, otherDlogSecret:String):Array[String] = {
    spendHalfMixBox(y, halfMixBoxId, otherInputBoxIds, feeAmount, changeAddress, Array(otherDlogSecret), true)
  }

  /*
Play Bob's role in creating a full-mix box with secret y.
halfMixBoxId is the boxId of the half-mix box created by an instance of Alice.
inputBoxIds are boxIds of additional input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting two full-mix box at index 0 and 1.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  private def spendHalfMixBox(y: BigInt, halfMixBoxId: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, proverDlogSecrets: Array[String], broadCast: Boolean) = {
    usingClient{implicit ctx =>
      val bob = new BobImpl(y.bigInteger)
      val halfMixBox: InputBox = ctx.getBoxesById(halfMixBoxId)(0)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val (fullMixTx, bit) = bob.spendHalfMixBox(HalfMixBox(halfMixBox), inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT]())
      if (broadCast) ctx.sendTransaction(fullMixTx.tx)
      Array(fullMixTx.tx.toJson(false), bit.toString)
    }
  }
}

object Carol {
  def getProveDlogAddress(z:BigInt):String = {
    val $INFO$ = "Utility method to compute proveDlog address for some secret (mostly for use in createFeeEmissionBox)"
    val gZ:GroupElement = g.exp(z.bigInteger)
    usingClient { implicit ctx =>
      val contract = ctx.compileContract(
        ConstantsBuilder.create().item(
          "gZ", gZ
        ).build(),"{proveDlog(gZ)}"
      )
      new Util().addressEncoder.fromProposition(contract.getErgoTree).get.toString
    }
  }

  /*
Fee Emission box is box that can be used to pay fee if the following conditions are satisfied:
(1) It is used for spending an ErgoMix Box that has the potential to break privacy via fee
(2) A fixed amount of fee is deducted
(3) Balance is put in an identical fee emission box

A fee emission box requires an additional secret z that can be used to withdraw the entire amount anytime
   */
  def createFeeEmissionBox(z:BigInt, amount:Long, inputBoxIds:Array[String], changeAddress:String,
                                 proverDlogSecret:String):String = {
    createFeeEmissionBox(z, amount, inputBoxIds, feeAmount, changeAddress, Array(proverDlogSecret), true)
  }

  /*
Fee Emission box is box that can be used to pay fee if the following conditions are satisfied:
(1) It is used for spending an ErgoMix Box that has the potential to break privacy via fee
(2) A fixed amount of fee is deducted
(3) Balance is put in an identical fee emission box

A fee emission box requires an additional secret z that can be used to withdraw the entire amount anytime
   */
  private def createFeeEmissionBox(z: BigInt, amount: Long, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, proverDlogSecrets: Array[String], broadCast: Boolean) : String = {
    usingClient{implicit ctx =>
      val carol = new CarolImpl(z.bigInteger)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val tx = carol.createFeeEmissionBox(amount, inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT]())
      if (broadCast) ctx.sendTransaction(tx.tx)
      tx.tx.toJson(false) // , tx.address.toString)
    }
  }
}
