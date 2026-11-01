package com.racheli.offlinenotes;
final class TaskItem {
    long id;
    String title, detail, dueDate;
    boolean done;
    long updatedAt;
    TaskItem(long id, String title, String detail, String dueDate, boolean done, long updatedAt) {
        this.id=id; this.title=title==null?"":title; this.detail=detail==null?"":detail;
        this.dueDate=dueDate==null?"":dueDate; this.done=done; this.updatedAt=updatedAt;
    }
}
