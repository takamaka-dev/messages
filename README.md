# Takamaka.io Messages

The examples are provided with prettified json, in the real application the 
jsons have to be minimized.

## External json envelope

All jsons with the same major version (e.g., 1.X) maintain backward 
compatibility; a parser created to read version 1.0 will be able to decode 
version 1.1 simply by ignoring fields not defined in the version. 
In the event that a field is introduced that breaks compatibility a new major 
version must be created. The json version corresponds to the field with the 
highest version number.

- **version**, key:"v", string like "1.0"
- **[action](#Action)**, key: "a", the data needed to perform the action
    - [fr](#From), key: "fr", from, the sender address in [Address](#Address) format
        - [t](#Address-Type) type
        - [ma](#Address-String) string
    - [to](#To) key: "to", to, the reciever address in [Address](#Address) format
        - [t](#Address-Type), key: "t", type
        - [ma](#Address-String), key: "ma", message address
    - [dt](#Date), key: "dt", date, unix time stamp millis
    - [g](#Green), key: "g", green, TKG value in nanoTK
    - [r](#Red), key: "r", red, value in nanoTK
    - [tm](#Text-Message), key: "tm", text message, UTF8 Text Message
    - [ew](#Encoded-Wallet), key: "ew", encoded wallet, Wallet encrypted with password
- **[type](#Type)**, key: "t", what action I expect to be performed by the qr
    - [st](#Stake-To-Node), key: "st", stake to node (v1.0)
    - [b](#Blob), key: "b", blob (v1.0)
    - [rp](#Request-Pay), key: "rp", request pay (v1.0)
    - [st](#Stake-To-Node), key: "st", stake to node (v1.0)
    - [su](#Steke-Undo), key: "su", stake undo (v1.0)
    - [we](#Wallet-Encrypted), key: "ew", wallet encrypted (v1.0)

## Action

### Fields

#### Date

a timestamp when required by the specific function, the timestamp is of type 
unix in milliseconds (e.g. 2024-10-17 8:01:51 GMT &rarr; 1729152111 )

#### Green

TKG value in nanoTKG (e.g. 10 000 000 000 nanoTKG &rarr; 10 TKG)

#### Red

TKR value in nanoTKG (e.g. 2 000 000 000 nanoTKG &rarr; 2 TKG)

#### Address

If it is an ed25519 the base64 URL encoding, if it is a qTesla the sha3-384 
encoded base64 URL. If it is an unrecognized object (e.g., an incorrect "to" 
field in which a string has been entered that does not fall into the previous 
cases) the sha3-384 encoded base64 URL of the same. If the address is in 
bas64url format and is 64 characters long it is considered as a compact address 
and used directly.

#### Text Message

A UTF8 free text message, for use within QRs it is advisable to keep under 
150 characters otherwise the QR becomes too large and loses readability 
by devices.

#### Encoded Wallet

A special transaction that allows the password-protected version of the wallet 
to be exported. The security of this transaction depends on how strong 
the password is. There is no significant difference in size between encryption 
types because what is exported is the seed generator for entropy and 
not the key.

The format of the content of the “ew” clause is defined by the 
takamaka.io core wallet library.

```json
{
    "version" : "0.1",
    "algorithm" : "AES",
    "wallet" : [ "uzEj+z98xCe38GfXDrVupQ==", "bpc ...[OMISSIS]... UQDL" ]
}
```

##### Address Type

 - **f** full address
 - **c** compact sha3-384 encoded base64 URL


##### Address String

#### From

Source Address

#### To

Destination address

## Type

The type of request action that the JSon or QR includes. This field is 
obbligatory.

### Fields

#### Stake To Node

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/STAKE.png)

```json
{
  "v" : "1.0",
  "a" : {
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10,
    "tm" : "test stake request"
  },
  "t" : "rp"
}
```

#### Blob

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/BLOB.png)

```json
{
  "v" : "1.0",
  "a" : {
    "tm" : "this is a message to be loaded in blockchain"
  },
  "t" : "b"
}
```

#### Pay

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/PAY.png)

```json
{
  "v" : "1.0",
  "a" : {
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10000000000,
    "r" : 2000000000,
    "tm" : "chiave qTesla + green + red"
  },
  "t" : "rp"
}
```

#### Stake Undo

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/STAKE_UNDO.png)

```json
{
  "v" : "1.0",
  "a" : {
    "dt" : 1729099034727,
    "tm" : "test stake undo request"
  },
  "t" : "rp"
}
```

#### Wallet Encrypted

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/WALLET_ENC.png)

```json
{
  "v" : "1.0",
  "a" : {
    "ew" : {
      "version" : "0.1",
      "algorithm" : "AES",
      "wallet" : [ "+DtiaqI+/xyAE7a/PCNW7w==", "AonDNdZsJFlbHznjAlPgufpL+OwnfMbPwJTOLqcFB+ZaxlB9O21SBn7xF11ah1UNXS4JtsTjfsbkXB2+NZsTPyYohbQmDp3iqSrkV2qPEPZkn6iU8+lbk93tvOubObgvNiYFY+9FhPWUFWi1tW8jbp0zzTF51vRGuDTTcaNjh7xYDu0VgsAhr4ogsg/bR97b2my6V5ulSW404f76bh9NUbEadcFS0zlyQr3C2tLmaFbTdRVd8XKA0Yn4iltUZF5SrppkS8eY+bYRr3sxstkpGZkeEl5ajsoBv5rSVMbn4SuN9l5sJZeoa8yr9RmZ7UKHRP9FdjaoVtsQfwioawxDBXcPoRcO96Kf0mB4lrkLdkpn9c8zhSfpwZ+AIAUkPHRurdufFFG3C8TaEpv+6SoeecNNuAGxOg4oQT1myabZqHSs9ei6PGzQpTIRuDzvLp5vj1kkghUvs265Tsl7LQ4W4sb859oxgIHqRV/IV682gdg=" ]
    }
  },
  "t" : "we"
}
```