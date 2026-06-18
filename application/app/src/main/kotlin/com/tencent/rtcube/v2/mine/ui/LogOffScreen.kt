package com.tencent.rtcube.v2.mine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.component.ConfirmDialog
import com.tencent.rtcube.v2.mine.store.MineState
import com.tencent.rtcube.v2.mine.store.MineStore

@Composable
internal fun LogOffScreen(
    store: MineStore,
    state: MineState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(70.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.mine_info_ic_back),
                contentDescription = stringResource(R.string.mine_info_back_desc),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 17.dp)
                    .size(38.dp)
                    .padding(10.dp)
                    .clickable { store.hideLogOff() },
            )
            Text(
                text = stringResource(R.string.mine_logoff_title),
                color = Color(0xFF000000),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(100.dp))

        Image(
            painter = painterResource(id = R.drawable.mine_about_ic_logoff),
            contentDescription = null,
            modifier = Modifier.size(width = 150.dp, height = 120.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.mine_logoff_hint),
            color = Color(0xFF333333),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = stringResource(R.string.mine_logoff_cur_account, state.userId),
            color = Color(0xFF999999),
            fontSize = 16.sp,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(width = 325.dp, height = 50.dp)
                .background(Color(0xFF006EFF), RoundedCornerShape(4.dp))
                .clickable { store.showLogOffDialog() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.mine_logoff_btn_text),
                color = Color.White,
                fontSize = 20.sp,
            )
        }
    }

    if (state.isLogOffDialogVisible) {
        ConfirmDialog(
            message = stringResource(R.string.mine_logoff_confirm),
            negativeText = stringResource(R.string.mine_common_btn_cancel),
            positiveText = stringResource(R.string.mine_common_btn_confirm),
            onNegative = { store.hideLogOffDialog() },
            onPositive = { store.logOff() },
        )
    }
}
