# Our Story — Java 3D Game Prototype - Under Development

This is the first playable prototype for a 12-month relationship memory adventure.

## Requirements
- JDK 17 or 21
- Gradle 8+ (or an IDE with Gradle support)
- Windows or macOS with OpenGL support
- Internet connection for the first build to download dependencies from Maven Central

## How to run
1. Extract this ZIP archive.
2. Open the `Our_Story` folder in IntelliJ IDEA.
3. Let Gradle download the necessary dependencies.
4. Open Gradle tasks and run **application > run**.

You can also run `gradle run` in the project directory if Gradle is installed.

Note: This project does not include a Gradle wrapper, so Gradle must be available on your machine or through your IDE.

## Controls
- `1` / `2`: select a character
- `Enter`: confirm selection or enter the game
- `W A S D` or arrow keys: move
- `E`: enter a nearby month portal
- `Backspace`: return to previous screen
- `Esc`: exit

## Included in this prototype
- Character selection with two placeholder 3D avatars
- Third-person camera and movement
- 12 month portals
- A simple explorable sample scene for each month
- English user interface

## Not implemented yet
- Realistic avatars based on photos
- Your real memories, locations, audio, and videos
- Two-player split-screen or online multiplayer
- Detailed models, animation, and photorealistic visuals

## Next steps
1. Write the story events for Month 1.
2. Replace placeholder avatars with custom models.
3. Implement local co-op and online client/server play.
4. Improve scene detail, animation, lighting, and sound.

**Note:** The current release uses placeholder geometry; it is not a photorealistic or fully multiplayer game.
