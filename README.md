# HueMuse
When you blink, your light blinks. Requires a Muse EEG Headset, and Philips Hue lights

I made this as a first step to hopefully try some real-time biofeedback using shifting lights. As of now it's a silly proof-of-concept.
Includes the official Muse SDK
Requires the OkHTTP3 library

This app doesn't natively connect to Philips Hue, it just sends a predetermined HTTP PUT request to the bridge, using OkHTTP.

This code borrows heavily from the libMuse example app and the OkHHTP example code. Hopefully I'll figure out how to officially credit them, but this is my first app, so I'm not sure what all the protocols are.

If by some random chance someone stumbles upon this repository, enjoy!
