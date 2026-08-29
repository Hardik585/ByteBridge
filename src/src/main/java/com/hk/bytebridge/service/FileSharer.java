package com.hk.bytebridge.service;

import com.hk.bytebridge.utils.UploadUtils;

import java.util.Map;

public class FileSharer {

    Map<Integer, String> available;

    public int offerPort(String filePath){
        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (!available.containsKey(port)) {
                available.put(port, filePath);
                return port;
            }
        }
    }
}
