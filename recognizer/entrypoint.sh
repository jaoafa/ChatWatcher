#!/bin/sh

# https://qiita.com/yoh1496/items/41179ce20340d02bd552
pulseaudio -D --exit-idle-time=-1
pactl load-module module-null-sink sink_name=MicOutput sink_properties=device.description="Virtual_Microphone_Output"
pacmd set-default-sink MicOutput
pacmd load-module module-virtual-source source_name=VirtualMic
pacmd set-default-source VirtualMic

pacmd load-module module-native-protocol-unix socket=/tmp/pulseaudio.socket

Xvfb :99 -ac -screen 0 1280x1024x16 -listen tcp &
x11vnc -forever -noxdamage -display :99 &

yarn build