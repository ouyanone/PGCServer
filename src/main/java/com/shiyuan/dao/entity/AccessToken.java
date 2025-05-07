package com.shiyuan.dao.entity;

public class AccessToken {
	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	@Override
	public String toString() {
		return "AccessToken [token=" + token + "]";
	}

}
