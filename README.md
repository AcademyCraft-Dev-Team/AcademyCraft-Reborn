# AcademyCraft-Reborn

A Minecraft mod about Academy City, for NeoForge 1.21.1.

## Downloads

*   **Latest Builds**: GitHub Actions (Recommended)
*   **Community (QQ Group)**: `217327418`

## License

This project is licensed under the GPL-3.0.

### How to build?

If the `AudioDecoderJNI` component has not been changed, you only need to run the following command:

```
./gradlew build
```

Otherwise, you will need to recompile the `audiodecoder_jni.c` file and place the resulting native library into the `src/main/resources/natives` directory.