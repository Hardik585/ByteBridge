package com.hk.bytebridge.utils;

import java.util.Random;

public class UploadUtils {

    public static int generateCode(){
        int dynamic_Starting_Port = 49152;
        int dynamic_Ending_Port = 65535;
        Random random = new Random();
        return random.nextInt(dynamic_Ending_Port - dynamic_Starting_Port) + dynamic_Starting_Port;
    }

}
