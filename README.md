# AcademyCraft-Reborn

A Minecraft mod about Academy City, for NeoForge.

## Downloads

*   **Latest Builds**: GitHub Actions (Recommended)
*   **Community (QQ Group)**: `217327418`

## License

This project is licensed under the GPL-3.0, with the following additional restrictions. The project is managed and maintained by the **AcademyCraft Dev Team**.

1.  **Redistribution**: All forms of redistribution must provide a direct link to the official project pages (e.g., GitHub repository, official CurseForge/Modrinth page). You may not re-upload the mod files to other websites or services.

2.  **Monetization**: All forms of commercial use are strictly prohibited. However, certain non-commercial funding methods are permitted under the conditions below.

    a. **Servers**: This mod may not be used on any server that sells gameplay advantages, items, currency, access, or any service for real money or its equivalent. Non-profit community servers may accept voluntary donations, provided that these donations are used exclusively to cover direct server operational costs (e.g., hosting fees) and grant no gameplay advantages to donors. Purely cosmetic rewards (such as a chat prefix) are permissible.

    b. **Contributors**: **Contributors to this project, including members of the AcademyCraft Dev Team,** may accept personal donations. However, such donations must not be presented as a payment for this mod, its features, or its continued development.

3.  **Derivative Works**: These restrictions apply in their entirety to any and all derivative works, including but not limited to forks, modifications, or any software that incorporates the original work in whole or in part.

4.  **Endorsement and Trademarks**: The name "AcademyCraft-Reborn", its logos, and other brand assets are the exclusive property of the **AcademyCraft Dev Team**. You may not use these assets to imply that your project, server, or service is officially endorsed or affiliated with the AcademyCraft-Reborn project without prior written permission from the team.

5.  **Platform Restrictions**: Porting, copying, or adapting this mod for any platform other than Minecraft: Java Edition with the NeoForge mod loader is prohibited. This includes, but is not limited to, Microsoft Minecraft (Bedrock Edition) and NetEase Minecraft.

6.  **Disclaimer of Warranty**: This software is provided "as is", without warranty of any kind, express or implied. The **AcademyCraft Dev Team**, as the project maintainer, is not liable for any claim, damages, or other liability, including but not limited to data loss or world corruption, arising from the use of this software.

### How to build?

```
./gradlew build
```

### IDEA Settings

If you are using IntelliJ IDEA, I recommend adding or replacing the following section in your `.idea/misc.xml` file:

```xml
<component name="EntryPointsManager">
    <list size="2">
        <item index="0" class="java.lang.String"
              itemvalue="org.misaka.api.common.network.annotation.SubscribePacket"/>
        <item index="1" class="java.lang.String"
              itemvalue="org.misaka.api.common.network.future.annotation.HandleFuture"/>
    </list>
</component>
```