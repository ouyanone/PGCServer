package com.shiyuan.dao.entity;

import java.util.Arrays;

public class GhinUser {
	
	private Golfers[] golfers;



	public Golfers[] getGolfers() {
		return golfers;
	}

	public void setGolfers(Golfers[] golfers) {
		this.golfers = golfers;
	}
	
	@Override
	public String toString() {
		return "GhinUser [golfers=" + Arrays.toString(golfers) + "]";
	}



}
