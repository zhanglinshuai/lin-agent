package com.lin.linagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class FileOperationToolTest {

    @Test
    void readFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "编程导航.txt";
        String result = fileOperationTool.readFile(fileName);
        System.out.println(result);
    }

    @Test
    void writeFile() {
    }

    @Test
    void listFiles() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String result = fileOperationTool.listFiles();
        System.out.println(result);
    }
}
