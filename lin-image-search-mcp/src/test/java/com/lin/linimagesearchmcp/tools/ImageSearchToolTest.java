package com.lin.linimagesearchmcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ImageSearchToolTest {

    @Autowired
    private ImageSearchTool imageSearchTool;

    @Test
    void searchImage() {
        String result = imageSearchTool.searchImage("李星云");
        System.out.println(result);
    }
}