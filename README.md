# AmIOnline

A lightweight Android app for checking whether a selected host or IP address responds to ICMP ping over Wi-Fi or Ethernet.

![](img/1.jpg)

![](img/2.jpg)

![](img/3.jpg)

![](img/4.jpg)

![](img/5.jpg)

![](img/6.jpg)

![](img/7.jpg)

## What the result means

- **ONLINE** means the selected target responded according to the device's ping utility exit status.
- **OFFLINE** means the ping utility did not report a successful reply, or the target did not respond.
- A successful ping only proves that the selected target was reachable for that check. It does **not** guarantee that general Internet access, DNS, HTTPS, or other services are working.
- Some networks and hosts block ICMP. A host or service may still be available even when ping reports no reply.
- Hostnames are validated locally; DNS resolution happens when the system ping utility executes.

## Development

The project uses Kotlin, Android Gradle Plugin, View Binding, AndroidX, Material Components, and Kotlin Coroutines.

Run unit tests with:

```sh
./gradlew test
```
