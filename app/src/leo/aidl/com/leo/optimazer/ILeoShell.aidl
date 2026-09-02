package com.leo.optimazer;

interface ILeoShell {
    void destroy() = 16777114;
    String execute(String command) = 1;
    int getServiceUid() = 2;
}
