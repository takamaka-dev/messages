# Takamaka.io Messages

The examples are provided with prettified json, in the real application the 
jsons have to be minimized.

## External Json Envelope

All jsons with the same major version (e.g., 1.X) maintain backward 
compatibility; a parser created to read version 1.0 will be able to decode 
version 1.1 simply by ignoring fields not defined in the version. 
In the event that a field is introduced that breaks compatibility a new major 
version must be created. The json version corresponds to the field with the 
highest version number.

- **[version](#External-Json-Envelope)**, key:"v", string like "1.0"
- **[action](#Action)**, key: "a", the data needed to perform the action
    - *[message of action](#Message-Of-Action)*
        - **[fr](#From)**, key: "fr", from, the sender address in [Address](#Address) format
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
    - *[Values For Type](#Values-For-Type)*
        - *[st](#Stake-To-Node), value: "st"*, stake to node (v1.0)
        - *[b](#Blob), value: "b"*, blob (v1.0)
        - *[rp](#Request-Pay), value: "rp"*, request pay (v1.0)
        - *[st](#Stake-To-Node), value: "st"*, stake to node (v1.0)
        - *[su](#Steke-Undo), value: "su"*, stake undo (v1.0)
        - *[we](#Wallet-Encrypted), value: "ew"*, wallet encrypted (v1.0)
- **[Type Of Signature](#Type-Of-Signature)**, key:"ts", string like "Ed25519BC" defined by the takamaka.io core wallet library

## Action

key **a**

### Message Of Action

#### Date

key **dt**

a timestamp when required by the specific function, the timestamp is of type 
unix in milliseconds (e.g. 2024-10-17 8:01:51 GMT &rarr; 1729152111 )

#### Green

key **g**

TKG value in nanoTKG (e.g. 10 000 000 000 nanoTKG &rarr; 10 TKG)

#### Red

key **r**

TKR value in nanoTKG (e.g. 2 000 000 000 nanoTKG &rarr; 2 TKG)

#### Address

keys **fr** or **to**

If it is an ed25519 the base64 URL encoding, if it is a qTesla the sha3-384 
encoded base64 URL. If it is an unrecognized object (e.g., an incorrect "to" 
field in which a string has been entered that does not fall into the previous 
cases) the sha3-384 encoded base64 URL of the same. If the address is in 
bas64url format and is 64 characters long it is considered as a compact address 
and used directly.

##### Address Type

key **t**

 - **f** full address
 - **c** compact sha3-384 encoded base64 URL


##### Address String

key **ma** message address

Base64 URL encoded byte string with "." has padding char

#### Text Message

key **tm**

A UTF8 free text message, for use within QRs it is advisable to keep under 
150 characters otherwise the QR becomes too large and loses readability 
by devices.

#### Encoded Wallet

key **ew**

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

#### From

key **fr**

Source Address of type [Address](#Address)

#### To

key **to**

Destination address of type [Address](#Address)

## Type

key **rp**

The type of request action that the Json or QR includes. This field is 
mandatory.

### Values For Type

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

## Type Of Signature

key **ts**

Defined by the takamaka.io core wallet library

```java
package io.takamaka.wallet.utils;

[...]

    public final class KeyContexts {

    [...]

        public final class KeyContexts {

        [...]

        public static enum WalletCypher {

            /**
             * currently not in use
             */
            Ed25519,
            /**
             * currently not in use
             */
            Tink,
            /**
             * current takamaka ed implementation
             */
            Ed25519BC,
            /**
             * bouncy castle, provable secure, I, Round 1
             */
            BCQTESLA_PS_1,
            /**
             * bouncy castle, provable secure, I, Round 2
             */
            BCQTESLA_PS_1_R2,
            /**
            * not for signature, key exchange
            */
            Curve25519BC

        }

        [...]

        }

    [...]

    }

```