package com.racheli.offlinenotes;

final class Note {
    long id;
    String title, body, imagePath, audioPath;
    long updatedAt;

    Note(long id, String title, String body, String imagePath, String audioPath, long updatedAt) {
        this.id=id;
        this.title=title==null?"":title;
        this.body=body==null?"":body;
        this.imagePath=imagePath;
        this.audioPath=audioPath;
        this.updatedAt=updatedAt;
    }
}
