package com.mengbo.mbdms.entity;

public class DmsUserInfo {
    public String accountId;
    public String nickName;
    public String phone;
    public String profileUrl;
    public int userType;
    public int gender;
    public byte age;
    public boolean isRelation;
    public boolean isCurrentAcc;

    public DmsUserInfo(
            String accountId,
            String nickName,
            String phone,
            String profileUrl,
            int userType,
            int gender,
            byte age,
            boolean isRelation,
            boolean isCurrentAcc
    ) {
        this.accountId = accountId;
        this.nickName = nickName;
        this.phone = phone;
        this.profileUrl = profileUrl;
        this.userType = userType;
        this.gender = gender;
        this.age = age;
        this.isRelation = isRelation;
        this.isCurrentAcc = isCurrentAcc;
    }
}
