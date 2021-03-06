/*
  MQTTManager.java

  Copyright (C) 2020  Davide Perini

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

  You should have received a copy of the MIT License along with this program.
  If not, see <https://opensource.org/licenses/MIT/>.
*/
package org.dpsoftware;

import com.sun.jna.Platform;
import javafx.scene.control.Alert;
import org.dpsoftware.config.Constants;
import org.dpsoftware.gui.GUIManager;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class MQTTManager implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(MQTTManager.class);

    MqttClient client;
    boolean connected = false;
    boolean reconnectionThreadRunning = false;

    /**
     * Constructor
     */
    public MQTTManager() {

        try {
            attemptReconnect();
        } catch (MqttException | RuntimeException e) {
            connected = false;
            FireflyLuciferin.communicationError = true;
            GUIManager guiManager = new GUIManager();
            guiManager.showAlert(Constants.MQTT_ERROR_TITLE,
                    Constants.MQTT_ERROR_HEADER,
                    Constants.MQTT_ERROR_CONTEXT, Alert.AlertType.ERROR);
            logger.error("Can't connect to the MQTT Server");
        }

    }

    /**
     * Reconnect to MQTT Broker
     * @throws MqttException can't handle MQTT connection
     */
    void attemptReconnect() throws MqttException {

        String mqttDeviceName;
        if (Platform.isWindows()) {
            mqttDeviceName = Constants.MQTT_DEVICE_NAME_WIN;
        } else {
            mqttDeviceName = Constants.MQTT_DEVICE_NAME_LIN;
        }
        client = new MqttClient(FireflyLuciferin.config.getMqttServer(), mqttDeviceName);
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setAutomaticReconnect(true);
        connOpts.setCleanSession(true);
        connOpts.setConnectionTimeout(10);
        connOpts.setMaxInflight(1000); // Default = 10
        connOpts.setUserName(FireflyLuciferin.config.getMqttUsername());
        connOpts.setPassword(FireflyLuciferin.config.getMqttPwd().toCharArray());
        client.connect(connOpts);
        client.setCallback(this);
        client.subscribe(FireflyLuciferin.config.getMqttTopic());
        logger.info(Constants.MQTT_CONNECTED);
        connected = true;
        
    }

    /**
     * Publish to a topic
     * @param msg msg for the queue
     */
    public void publishToTopic(String msg) {

        MqttMessage message = new MqttMessage();
        message.setPayload(msg.getBytes());
        try {
            client.publish(FireflyLuciferin.config.getMqttTopic(), message);
        } catch (MqttException e) {
            logger.error(Constants.MQTT_CANT_SEND);
        }

    }

    /**
     * Stream messages to the stream topic
     * @param msg msg for the queue
     */
    public void stream(String msg) {

        try {
            client.publish(FireflyLuciferin.config.getMqttTopic() + Constants.MQTT_STREAM_TOPIC, msg.getBytes(), 0, false);
        } catch (MqttException e) {
            logger.error(Constants.MQTT_CANT_SEND);
        }

    }

    /**
     * Reconnect on connection lost
     * @param cause MQTT connection lost cause
     */
    @Override
    public void connectionLost(Throwable cause) {

        logger.error("Connection Lost");
        connected = false;
        if (!reconnectionThreadRunning) {
            ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                if (!connected) {
                    try {
                        client.setCallback(this);
                        client.subscribe(FireflyLuciferin.config.getMqttTopic());
                        connected = true;
                        logger.info(Constants.MQTT_RECONNECTED);
                    } catch (MqttException e) {
                        logger.error(Constants.MQTT_DISCONNECTED);
                    }
                }
            }, 0, 10, TimeUnit.SECONDS);
        }

    }

    /**
     * Subscribe to the topic to START/STOP screen grabbing
     * @param topic MQTT topic where to publish/subscribe
     * @param message MQTT message to read
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) {

        logger.info(String.valueOf(message));
        if (message.toString().contains(Constants.MQTT_START)) {
            FireflyLuciferin.guiManager.startCapturingThreads();
        } else if (message.toString().contains(Constants.MQTT_STOP)) {
            FireflyLuciferin.guiManager.stopCapturingThreads();
        }

    }

    /**
     * Callback for MQTT message sent
     * @param token mqtt token
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        //logger.info("delivered");
    }

}