# Gaia

Get notifications if your washing machine/dishwasher/whatever device finishes. To use this, you may need to have a FritzDECT device like this: https://avm.de/produkte/fritzdect/fritzdect-200 as it reads and monitors the energy consumed by the device connected to it. It also acts as a bridge to Prometheus for the metrics.

## Configuration

Create an `application.yml` file to configure services and appliances:

```yaml
gaia:
  fritzbox:
    username: 
    password: 
    baseUrl: http://fritz.box
    appliances:
      washingMachine:
        ain: xxx
        discordNotificationStarted: "The washing machine was started." # optional if you don't want a discord notification
        discordNotificationStopped: "The washing machine has finished." # optional if you don't want a discord notification
        todoistTaskStarted: "It was started!" # optional if you don't want a task to be created
        todoistTaskStopped: "Now it stopped" # optional if you don't want a task to be created
        queueSize: 10 # time frame in seconds to collect power consumption, optional if you don't need to trigger anything
        thresholdStart: 8.0 # Watts, optional if you don't need to trigger anything
        thresholdEnd: 8.0 # Watts, optional if you don't need to trigger anything

  discord:
    webhookUrl: <discord-webhook-url>
    username: Gaia

  todoist:
    webhookUrl: <todoist-webhook-url>
    accessToken: <todoist-access-token>
    projectId: <todoist-project-id>

server:
  port: 9000

management:
  endpoints:
    web:
      exposure:
        include: [ "prometheus" ]
```

Note, that every device registered in your FritzBox will be tracked, regardless of it being mapped in the `appliances` section.
However there will be no Discord notifications for it and also no Todoist tasks will be created.

## Build & run it

```sh
./gradlew bootBuildImage
docker run -v $(pwd)/application.yml:/workspace/config/application.yml -p 9000:9000 domnikl/gaia
```
