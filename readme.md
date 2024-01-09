# Simple Voice Chat Radio

A server-side Simple Voice Chat addon mod for Fabric adding a radio block capable of streaming mp3 radio streams.

## Disclaimer

This mod may not be able to play all mp3 streams.
The mp3 stream must be playable by [JLayer](https://web.archive.org/web/20210108055829/http://www.javazoom.net/javalayer/javalayer.html).
**Please don't report issues if the stream you are trying to play is not working.**

## Usage

- Install this mod alongside [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) on your server
- Make sure you set up the voice chat mod correctly (see [here](https://modrepo.de/minecraft/voicechat/wiki/server_setup))
- Get the mp3 livestream URL of your favorite radio station
- Enter the command `/radio create "<stream-url" "<station-name>"` to create a radio block
- Place the radio block
- Right-click the radio block to turn it on

## Commands

- `/radio create "<stream-url" "<station-name>" [sound_radius]` - Gives you a radio playing the provided mp3 stream. If the
radius isn't provided, the server default (48, in the config) is used.

## How to get Radio Stream URLs

You might find the stream URL on your radio stations' website.
If you want to search for a radio station, [streamurl.link](https://streamurl.link/) might have it.

## Configuration

| Property                   | Description                                            | Default |
|----------------------------|--------------------------------------------------------|---------|
| `radio_range`              | The audible range of radios                            | `48.0`  |
| `command_permission_level` | The permission level required to use the radio command | `0`     |
| `radio_skin_url`           | The skin url for the radio block                       |         |
| `show_music_particles`     | Whether to show music particles                        | `true`  |
| `music_particle_frequency` | The frequency of the music particles in milliseconds   | `2000`  |

## Credits

- [JLayer](https://web.archive.org/web/20210108055829/http://www.javazoom.net/javalayer/javalayer.html)
- [Radio Skin](https://minecraft-heads.com/custom-heads/decoration/215-radio)