package koren.swiftescaper.ui.view.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minew.beaconset.MinewBeacon
import koren.swiftescaper.R
import koren.swiftescaper.domain.viewmodel.MainViewModel
import koren.swiftescaper.ui.theme.BlueGray
import koren.swiftescaper.ui.theme.Gray

@Composable
fun BeaconList(
    modifier: Modifier,
    viewModel: MainViewModel) {
    val filteringBeacons = viewModel.filteringBeacons.collectAsState()

    LazyColumn (modifier = modifier){
        itemsIndexed(filteringBeacons.value) {index, beacon ->
            BeaconItems(modifier = modifier, beacon = beacon, rssi = beacon.rssi)
        }
    }
}

@Composable
fun BeaconItems(
    modifier: Modifier,
    beacon : MinewBeacon,
    rssi : Int) {
    Row(modifier = modifier
        .fillMaxWidth()
        .padding(10.dp)) {
        Box(modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Gray)
            .size(45.dp),
            contentAlignment = Alignment.Center
            ){
            Image(painter = painterResource(id = R.drawable.beacon_ic), contentDescription = null)
        }
        Spacer(modifier = modifier.width(10.dp))
        Column {
            Text(text = beacon.name,
                fontSize = 16.sp)
            Text(text = "Signal Rssi : ${rssi.toString()}",
                fontSize = 14.sp,
                color = BlueGray)
        }
    }
}