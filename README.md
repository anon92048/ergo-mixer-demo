# ErgoMix


This repository contains implementation of ErgoMix (a.k.a ZeroJoin), non-interactive (and non-custodial) mixing scheme on top of the [Ergo Platform](https://ergoplatform.org/en/) blockchain.
The scheme was designed by Ergo core developers (kushti and scalahub), and described in a paper coming (a draft is available @ https://github.com/anon92048/ergo-cli-mixer/blob/master/paper/ergomix.pdf )

## Why ErgoMix


* It is non-interactive, so no any channel is needed outside of the [Ergo blockchain](https://ergoplatform.org/en/). It helps to avoid a lot of problems with network-level security and also allows for bigger rings.
* No limit on ring size, so ErgoMix can provide security similar to Monero and ZCoin after enough mixes done (ErgoMix is not hiding amounts though).
* Many mixing schemes have flaws because of transaction fees to be paid to a miner. ErgoMix paper is proposing a solution based on 
fee tokens (consumed by a box emitting fees, see [https://www.ergoforum.org/t/paying-fee-in-ergomix-in-primary-tokens/73](https://www.ergoforum.org/t/paying-fee-in-ergomix-in-primary-tokens/73)). 

## How To Play With The Demo

Use "mixer.jar" from the root folder of the repo, or build it on your own with "sbt assembly" (& maybe renaming resulting .jar file as I did).

ErgoMix works as follows:

* Alice creates a half-mix box on the blockchain protected by a special mixing contract. Then she just listens to the blockchain.
* Bob comes and creates a transaction which consumes Alice's box and produces two "full-mix" boxes which are looking indistinguishable for an external observer. One of these boxes could be spent by Alice only and another by Bob only though.
* "Full-mix" box can be withdrawn or remixed with another half-mix box. Here fee issue arises, and so special "emission box" is needed (currently it is not using a token but it will in the future).

Alice and Bob are associated with secret and corresponding public keys.

So first let's create Alice and Bob. For that, we need to create secret big numbers (not big at all so not cure in the examples. Please create a really big and random number from 1 to 115792089237316195423570985008687907852837564279074904382605163141518161494337). 


==========================================================

First, create a secret for Alice, get "her" P2PK (Pay-to-Public-Key) address.

Alice: 67850

java -cp mixer.jar ProveDlogAddress --secret 67850 --url http://88.198.13.202:9053/ --mainnet true

9i5hNNhtJL2Typ2HGK1xyaAVPUwDdjSea9cdEcwnPiTusmwJNWx


Send some Ergs to the adress (more than 0.1 Erg, e.g. 0.5-1 Erg).

---------------------------------------------------------------------------------------------------

The same for Bob (send some money to "him" also).

Bob: 77850

java -cp mixer.jar ProveDlogAddress --secret 77850 --url http://88.198.13.202:9053/ --mainnet true

9g5UC4CbF2cRzDs6zsSDB1AHakFqk7ceHYoGPnydagG4a4aiiAM

---------------------------------------------------------------------------------------------------

Create an address to fund an emission box.

PreEmission: 107850

java -cp mixer.jar ProveDlogAddress --secret 107850 --url http://88.198.13.202:9053/ --mainnet true

9g24o8ykiKQ76R4Ziqw43P3WgTzu3Hz8Qvt2YMGENDnzmo8Xzc8

Send money to it (more than 0.0025 Erg). Now find a transaction in e.g. explorer (first by  looking for the address https://explorer.ergoplatform.com/en/addresses/9g24o8ykiKQ76R4Ziqw43P3WgTzu3Hz8Qvt2YMGENDnzmo8Xzc8 , then find the transaction there: https://explorer.ergoplatform.com/en/transactions/9c467043f4f78735d785a9b353b13320a879920fcf7d8a27fdf58a295d9a645f ) to get output created for the address (bf1bda5eb953305323ff0a9b175498be3a5bae1450885e5dea6d10e2201ab7bd )


Create an emission box with the output created. Set amount to be not less than 1500000. "dlogSecret" is a secret for the previous address.

Emission box secret: 111950

java -cp mixer.jar NewFeeEmissionBox --secret 111950 --amount 100000000 --inputBoxId bf1bda5eb953305323ff0a9b175498be3a5bae1450885e5dea6d10e2201ab7bd --changeAddress 9hsQktNvigHUZV5r8QYxXNfgdVgCjCX6K9wEMQ8hjZz1E1SpEmA --dLogSecret 107850 --url http://88.198.13.202:9053/ --mainNet true

emission box id: a2ef136da5c041f86f2c527484688c983fd98eff48f5289fb17014ed32b00a20

---------------------------------------------------------------------------------------------------

Now Alice creates a half-mix box (with a new secret). Find output to spend as in emission case above. Half-mix box should be of certain value (0.1 Erg), rest will be sent to "changeAddress".

Alice Entry: 97650

java -cp mixer.jar AliceEntry --secret 97650 --inputBoxId d22326fc68e9385785c1d7775474155b261cf83e63d9fa173786d82b5423e168 --changeAddress 9hsQktNvigHUZV5r8QYxXNfgdVgCjCX6K9wEMQ8hjZz1E1SpEmA --dLogSecret 67850 --url http://88.198.13.202:9053/ --mainnet true

tx id: 8f5fdd72f726c752c801e29652b8d334995e2295a5960b3e32bd6bfd804310bd

box id: fcc58130dfe01705c9c4026bb81056510a80f68ef7346cbf6d5854392c24b3d8

----------------------------------------------------------------------------------------------------


Now Bob is doing mixing with Alice's half-mix box:

Bob Entry: 117550

java -cp mixer.jar BobEntry --secret 117550 --halfMixBoxId fcc58130dfe01705c9c4026bb81056510a80f68ef7346cbf6d5854392c24b3d8 --inputBoxId 27146028f9efc38aeb03a42792a8f9bffcd507e7ee4f3db24183d4d107a1d63e --changeAddress 9hsQktNvigHUZV5r8QYxXNfgdVgCjCX6K9wEMQ8hjZz1E1SpEmA --dLogSecret 77850 --url http://88.198.13.202:9053/ --mainNet true

tx id: 63deb3c79c5ae85916fd8179326f01e7a10cf3a2ee931e641ebffb59fcdcb917

The transaction creates two full-mix boxes.

-----------------------------------------------------------------------------------------------------

Alice can withdraw a full-mix box created for her. 

Withdraw - Alice:

java -cp mixer.jar FullMixBoxWithdraw --mode alice --secret 97650 --fullMixBoxId f37c4ce3470b4eab3a8394b1aca60c7817866554c0436a978fd8688118311190 --withdrawAddress 9hsQktNvigHUZV5r8QYxXNfgdVgCjCX6K9wEMQ8hjZz1E1SpEmA --feeEmissionBoxId a2ef136da5c041f86f2c527484688c983fd98eff48f5289fb17014ed32b00a20 --url http://88.198.13.202:9053/ --mainNet true

tx id: 40ea89540eece3634edb8932c60845109d87bb5c57841c38ab38c8f85ffa15c3

------------------------------------------------------------------------------------------------------

Alice can create a new half-mix box:

Alice Entry 2: 197650

java -cp mixer.jar AliceEntry --secret 197650 --inputBoxId 0b03f371330e590375d45ccab0965a821f78699f2873d9a040cb70fea7a1f584 --changeAddress 9hsQktNvigHUZV5r8QYxXNfgdVgCjCX6K9wEMQ8hjZz1E1SpEmA --dLogSecret 67850 --url http://88.198.13.202:9053/ --mainnet true

------------------------------------------------------------------------------------------------------

And Bob can mix his full-mix box with the new half-mix box: 

Remix - Bob: 114600

java -cp mixer.jar FullMixBoxRemixAsBob --mode bob --secret 117550 --fullMixBoxId f37c4ce3470b4eab3a8394b1aca60c7817866554c0436a978fd8688118311190 --nextSecret 114600 --halfMixBoxId ef2de35b679fd689f8c53ed919f16a0ebd8b9fcd12abf5a3d417b1664d433bbe  --feeEmissionBoxId c322158365bba4a288d03e9579242f7be2c9d2b83a8eaa3ed16d9489d010bf4e --url http://88.198.13.202:9053/ --mainNet true


## Further Developments

* Track half-mixed boxes, emission boxes, and own boxes.
* Fee tokens support (create many emission boxes for mixes).
* Automated mixing which will do enough mixes with Alice and Bob roles (a role is to be chosen by a random coin at each step). 
* UI
