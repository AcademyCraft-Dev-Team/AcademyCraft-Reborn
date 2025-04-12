package org.academy.api.common.wireless;

import java.util.List;

public interface WirelessNode {
    String getNodeName();
    void setNodeName(String nodeName);
    String getNodePassword();
    void setNodePassword(String nodePassword);
    int getRadius();
    int getEnergyStored();
    void setEnergyStored(int energyStored);
    int getMaxEnergyStorage();
    int getTranslateSpeed();
    List<WirelessUser> getWirelessNodes();
}