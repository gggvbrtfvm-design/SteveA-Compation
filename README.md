# Steve AI Companion

Minecraft Java 1.21.11 + Fabric, Java 21.

## Current prototype features
- `/aicall` summons one companion for the player.
- `/aidespawn` dismisses that player's companion.
- `/aiste <Turkish instruction>` sends a request to Gemini and receives a Turkish response.
- Basic follow, stop, wait, and come-here behaviors.
- Gemini requests run asynchronously; API keys are stored in the local config file, not in the source code.
- AI responses are restricted to a small allowlist of actions. Unsupported tasks are not falsely reported as completed.

## Gemini setup
1. Launch the game once with the mod installed.
2. Open `.minecraft/config/steve-ai-companion.properties`.
3. Put your Gemini API key in `api_key=`.
4. Keep this file private. Never upload it or share the key.
5. Restart the game.

The default model setting is `gemini-4-argon`. If Google changes model availability, replace the `model=` value with a model enabled for your API key.

## Important status
This is an early functional AI/chat and companion-behavior milestone, not the finished feature set. The current entity is still a temporary vanilla placeholder, not a custom human Steve model. Autonomous building, block gathering, crafting, exploration, voice, and a proper player-style renderer are not implemented yet.
