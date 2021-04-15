# Gaia

Get Discord notifications if your washing machine/dish washer/whatever device finishes.
To use this, you may need to have a FritzDECT device like this: https://avm.de/produkte/fritzdect/fritzdect-200 as it
reads and monitors the consumed by the device connected to it.  

## Configuration

You need to configure the following parameters, put them in `gaia.conf` and provide it as the first argument when running Gaia like this: `java -jar gaia.jar gaia.conf`.

```hocon
fritzBox {
    user = "<your-fritz-box-user>" # user is empty for the default case
    password = "<your-fritz-box-password>"
    url = "http://fritz.box"
}

actors {
    0: {
        ain = "<device-ain>"
        name = "washing machine"
        messageStart = "Die Waschmaschine legt jetzt los"
        messageEnd = "Die Waschmaschine ist fertig, bitte Wäsche aufhängen!"
        queue {
            size = 10 # retention of 10s (means collect a value every second, keep values for 10s)
            thresholdStart = 0.1 # watts
            thresholdEnd = 4.0 # watts
        }
    },
    1: {
        ain = "<device-ain>"
        name = "dish washer"
        messageStart = "Die Spülmaschine nimmt ihren Dienst auf"
        messageEnd = "Die Spülmaschine ist fertig, bitte ausräumen!"
        queue {
            size = 60
            thresholdStart = 0.1
            thresholdEnd = 4.0
        }
    }
}

discord {
    token = "<your-discord-bot-token>"
    channel = general
}
```
