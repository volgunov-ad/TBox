package com.openos.weather;

import android.provider.BaseColumns;
import kotlin.Metadata;

/* compiled from: Constant.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000¨\u0006\u0006"}, d2 = {"Lcom/openos/weather/WeatherEntry;", "Landroid/provider/BaseColumns;", "()V", "COLUMN_NAME_WEATHER_DATA", "", "COLUMN_NAME_WEATHER_ID", "openos_sdk_wt30RRelease"}, k = 1, mv = {1, 4, 1})
/* loaded from: classes2.dex */
public final class WeatherEntry implements BaseColumns {
    public static final String COLUMN_NAME_WEATHER_DATA = "data";
    public static final String COLUMN_NAME_WEATHER_ID = "weather_id";
    public static final WeatherEntry INSTANCE = new WeatherEntry();

    private WeatherEntry() {
    }
}
