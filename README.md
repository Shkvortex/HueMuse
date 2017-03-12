# HueMuse
When you blink, your light blinks. Requires a Muse EEG Headset, and Philips Hue lights

I made this as a way to learn Java and REST apis, and as a first step to hopefully try some real-time biofeedback using shifting lights. As of now it's a silly proof-of-concept.
Includes the official Muse library.
Requires the OkHTTP3 library

This app doesn't natively connect to Philips Hue, it just sends a predetermined HTTP PUT request to the bridge, using OkHTTP. This means if you want to use it, you'll need to find your own Hue Bridge's api and whitelist a new username to access it.

This code borrows heavily from the libMuse example app and the OkHHTP example code. Hopefully I'll figure out how to officially credit them, but this is my first app, so I'm not sure what the etiquette is.

If by some random chance someone stumbles upon this repository, enjoy!
