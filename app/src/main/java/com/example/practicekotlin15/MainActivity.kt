package com.example.practicekotlin15

import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.*
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MarkerIcons
import com.naver.maps.map.widget.LocationButtonView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback, Overlay.OnClickListener{
// 구현을 위해서 OnMapReadyCallback 메서드 implement
// marker 클릭 구현을 위해서 MainActivity를 구현체로 하는 Overlay.OnClickListener implement
// MainActivity에 구현했기 때문에 MainActivity는 구현체

    private lateinit var naverMap: NaverMap
    private lateinit var locationSource: FusedLocationSource

    private val mapView: MapView by lazy {
        findViewById(R.id.mapView)
    }

    private val viewPager: ViewPager2 by lazy {
        findViewById(R.id.houseViewPager)
    }

    private val recyclerView: RecyclerView by lazy {
        findViewById(R.id.recyclerView)
    }
    
    private val currentLocationButton: LocationButtonView by lazy {
        findViewById(R.id.currentLocationButton)
    }

    private val bottomSheetTileTextView: TextView by lazy {
        findViewById(R.id.bottomSheetTitleTextView)
    }

    private val viewPagerAdapter = HouseViewPagerAdapter(itemClicked = { // 공유하기
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "[지금 이 가격에 예약하세요!!] ${it.title} ${it.price} 사진보기: ${it.imgUrl}")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, null))
    })

    private val recyclerAdapter = HouseListAdapter()


    // 네이버 지도 초기화
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync(this) // map 객체 생성

        viewPager.adapter = viewPagerAdapter
        recyclerView.adapter = recyclerAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() { // viewPager 전환 시, 해당되는 marker와 연동

            override fun onPageSelected(position: Int) { // 아이템이 전환될 때
                super.onPageSelected(position)

                val selectedHouseModel = viewPagerAdapter.currentList[position]
                val cameraUpdate = CameraUpdate.scrollTo(LatLng(selectedHouseModel.lat, selectedHouseModel.lng))
                    .animate(CameraAnimation.Easing)

                naverMap.moveCamera(cameraUpdate)
            }
        })
    }

    // onMapReady가 호출이 돼 지도가 나타나고, 그 이후에 api를 호출하여 데이터를 가져온 다음
    // 데이터를 가져온 이후에 호텔들의 위치를 나타내는 마커를 가져옴 (동기)

    override fun onMapReady(map: NaverMap) {
        naverMap = map

        naverMap.maxZoom = 18.0 // 최대 줌 정도 설정
        naverMap.minZoom = 10.0 // 최소 줌 정도 설정

        val cameraUpdate = CameraUpdate.scrollTo(
            LatLng(
                37.55969188915907,
                126.91599231713168
            )
        ) // 초기 위치를 설정을 위한 좌표 설정
        naverMap.moveCamera(cameraUpdate) // 초기 위치 설정

        val uiSetting = naverMap.uiSettings
        uiSetting.isLocationButtonEnabled = false // 현 위치 버튼 활성화, manifest 설정과 request 팝업 구현 필수
        currentLocationButton.map = naverMap // 현 위치 버튼의 위치를 커스텀 함에따라, 기존 isLocationButtonEnabled는 false로 비활성화 시킨 뒤 재정의 한 버튼을 적용

        locationSource = FusedLocationSource(
            this@MainActivity,
            LOCATION_PERMISSION_REQUEST_CODE
        ) // 권한에 이상이 없을 경우, 현위치 기능 실행
        naverMap.locationSource = locationSource // 구글의 locationSource를 네이버 locationSource에 연결

        getHouseListFromAPI()
    }

    private fun getHouseListFromAPI() { // retrofit 환경 설정
        val retrofit = Retrofit.Builder()
            .baseUrl("https://run.mocky.io")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(HouseService::class.java).also { // also -> 만든 이후에 라는 의미
            it.getHouseList()
                .enqueue(object : Callback<HouseDto> {
                    override fun onResponse(call: Call<HouseDto>, response: Response<HouseDto>) {
                        if (response.isSuccessful.not()) {
                            // 실패 처리에 대한 구현
                            return
                        }
                        response.body()?.let { dto ->
                            updateMarker(dto.items)
                            viewPagerAdapter.submitList(dto.items) // 불러온 houseModel 정보를 그대로 viewPager에 표시
                            recyclerAdapter.submitList(dto.items)

                            bottomSheetTileTextView.text = "${dto.items.size}개의 숙소"
                        }
                    }

                    override fun onFailure(call: Call<HouseDto>, t: Throwable) { // 실패 처리에 대한 구현

                    }

                })
        }
    }
    
    private fun updateMarker(houses: List<HouseModel>) {
        houses.forEach { house ->
            val marker = Marker()
            marker.position = LatLng(house.lat, house.lng)
            marker.onClickListener = this
            marker.map = naverMap
            marker.tag = house.id
            marker.icon = MarkerIcons.BLACK
            marker.iconTintColor = Color.RED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return
        }

        if (locationSource.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
            )
        ) { // 권한 팝업 사용을 좀 더 쉽게 하기 위해 구글의 라이브러리 사용
            if (!locationSource.isActivated) {
                naverMap.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onClick(overlay: Overlay): Boolean { //overlay -> 마커의 총집합
        overlay.tag

        val selectedModel = viewPagerAdapter.currentList.firstOrNull {
            it.id == overlay.tag
        }

        selectedModel?.let {
            val position = viewPagerAdapter.currentList.indexOf(it)
            viewPager.currentItem = position
        }

        return true // onClick에 대한 return 값 반환
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}
