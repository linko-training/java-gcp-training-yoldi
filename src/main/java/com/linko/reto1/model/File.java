package com.linko.reto1.model;

public class File {

    private String id;
    private String name;
    private long size;
    private String url;

    public File(String id, String name, long size, String url) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
