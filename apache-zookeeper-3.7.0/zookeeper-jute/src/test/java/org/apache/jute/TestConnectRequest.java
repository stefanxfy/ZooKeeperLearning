package org.apache.jute;

import org.apache.zookeeper.proto.ConnectRequest;
import org.junit.jupiter.api.Test;

import java.io.*;

public class TestConnectRequest {

    @Test
    public void test() throws IOException {
        ConnectRequest connectRequest = new ConnectRequest();
        connectRequest.setProtocolVersion(2); // int 4字节 0 0 0 2
        connectRequest.setLastZxidSeen(10000);// long 8字节 0 0 0 0 0 0 39 16
        connectRequest.setSessionId(123456789);// long 8字节 0 0 7 -48 0 0 0 0
        connectRequest.setTimeOut(2000); // int 4字节 7 91 -51 21
        connectRequest.setPasswd("2222".getBytes()); // 先记录byte[]长度4字节，然后是byte[]内容 0 0 0 4 50 50 50 50
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputArchive outputArchive = new BinaryOutputArchive(new DataOutputStream(bos));
        connectRequest.serialize(outputArchive, "connectRequest");
        for (byte b : bos.toByteArray()) {
            System.out.print(b);
            System.out.print(" ");
        }
        // 0 0 0 2, 0 0 0 0 0 0 39 16, 0 0 7 -48 0 0 0 0, 7 91 -51 21, 0 0 0 4 50 50 50 50
        System.out.println();

        InputArchive inputArchive = new BinaryInputArchive(new DataInputStream(new ByteArrayInputStream(bos.toByteArray())));
        ConnectRequest connectRequest2 = new ConnectRequest();
        connectRequest2.deserialize(inputArchive, "connectRequest");
        System.out.println(connectRequest.toString());
        System.out.println(connectRequest2.toString());
    }
}
