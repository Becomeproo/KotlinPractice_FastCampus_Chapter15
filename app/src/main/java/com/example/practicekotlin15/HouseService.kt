package com.example.practicekotlin15

import retrofit2.Call
import retrofit2.http.GET

interface HouseService {
    @GET("/v3/ddaa2b01-d6e0-4e3d-9a06-58e120084384")
    fun getHouseList(): Call<HouseDto>
}