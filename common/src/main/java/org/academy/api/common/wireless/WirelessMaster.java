package org.academy.api.common.wireless;

import java.util.List;

public interface WirelessMaster {
    String getName();
    String getPassword();
    int getRadius();
    int getEnergyStored();
    int getMaxEnergyStorage();
    int getTranslateSpeed();
    List<WirelessNode> getWirelessNodes();
}