package com.leo.optimazer;

interface ILeoShell {
    String execute(String command);
    int getServiceUid();
    void destroy();
}
