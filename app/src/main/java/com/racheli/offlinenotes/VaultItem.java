package com.racheli.offlinenotes;
final class VaultItem {
    long id, size, createdAt;
    String name, mimeType, path;
    VaultItem(long id,String name,String mimeType,String path,long size,long createdAt) {
        this.id=id; this.name=name==null?"קובץ":name; this.mimeType=mimeType==null?"application/octet-stream":mimeType;
        this.path=path; this.size=size; this.createdAt=createdAt;
    }
}
