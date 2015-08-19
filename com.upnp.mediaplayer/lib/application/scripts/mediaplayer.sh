#!/bin/sh
#
# Wrapper script for Pete Mancherster's MediaPlayer,
# an implementation of an OpenHome+UPnP/DLNA media
# renderer. Copy to, e.g. /usr/local/bin/mediaplayer
#
# https://github.com/PeteManchester/MediaPlayer
#

# Change this to where the mediaplayer JAR is located
MEDIAPLAYER_DIR=/opt/mediaplayer

cd $MEDIAPLAYER_DIR
exec -a mediaplayer java -jar mediaplayer.jar
