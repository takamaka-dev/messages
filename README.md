# Takamaka.io Messages

## External json envelope

All jsons with the same major version (e.g., 1.X) maintain backward 
compatibility; a parser created to read version 1.0 will be able to decode 
version 1.1 simply by ignoring fields not defined in the version. 
In the event that a field is introduced that breaks compatibility a new major 
version must be created. The json version corresponds to the field with the 
highest version number.

 - [st](#Stake-To-Node) stake to node (v1.0)
 - [b] blob (v1.0)
 - [rp] request pay (v1.0)
 - [st] stake to node (v1.0)
 - [su] stake undo (v1.0)
 - [we] wallet encrypted (v1.0)


### Stake To Node



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