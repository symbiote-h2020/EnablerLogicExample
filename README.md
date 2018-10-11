# Enabler Logic Example

This is example used in this documentation 
[https://github.com/symbiote-h2020/EnablerLogic](https://github.com/symbiote-h2020/EnablerLogic).

The Enabler Logic Eaxample (ELE) registers 3 resources:

- Sensor with temperature and humidity
- Actuator for starting and stopping Air Condition (AC)
- Service for controlling humidity (perameters are OFF or target humidity)

The logic is explained in following activity diagram.

![](resources/ELE_logic.png)

## 1. Starting Enabler

Whena enabler is started first thing it do is registering resouces, then it register RAP consmers and then it sends message to Enabler Resource manager to start task about getting temperature data on periodic basis. 

This last proces is shown in following figure.

![](resources/Starting.png)

## 2. Reciving data

When there is time to receive data Enabler Platform Proxy startes requests to resources which is explained in following diagram.

![](resources/MeasurementAppeared.png)

After measurement appeared Enabler Platform Proxy send message about that to Enabler Logic and it calls method `measurementReceived` in `EnablerLogic` class (see following diagram).

![](resources/MeasurementReceived.png)

After that logic is started.

## 3. Handling Reading Sensor Data in Enabler Logic

![](resources/Requesting_temp.png)

## 4. Handling Actuating Resource in Enabler Logic

![](resources/Actuating.png)

## 5. Handling Invoking Service in Enabler Logic

![](resources/InvokingService.png)
