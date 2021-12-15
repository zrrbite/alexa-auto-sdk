package com.amazon.alexa.auto.templateruntime.receiver;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.amazon.aacsconstants.Action;
import com.amazon.aacsconstants.Topic;
import com.amazon.alexa.auto.aacs.common.AACSMessage;
import com.amazon.alexa.auto.templateruntime.common.TestResourceFileReader;
import com.amazon.alexa.auto.templateruntime.receiver.TemplateRuntimeReceiver;
import com.amazon.alexa.auto.templateruntime.weather.WeatherDirectiveHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class TemplateRuntimeReceiverTest {
    private TemplateRuntimeReceiver mClassUnderTest;
    private WeatherDirectiveHandler mWeatherDirectiveHandler;

    @Before
    public void setup() {
        mClassUnderTest = new TemplateRuntimeReceiver();
        mWeatherDirectiveHandler = Mockito.mock(WeatherDirectiveHandler.class);
        mClassUnderTest.mWeatherDirectiveHandler = mWeatherDirectiveHandler;
    }

    @Test
    public void renderWeatherTemplateTest() throws JSONException {
        Intent renderTemplateIntent =
                generateIntent("aacs/RenderTemplateWeather.json", "com.amazon.aacs.aasb.RenderTemplate");
        mClassUnderTest.onReceive(Mockito.mock(Context.class), renderTemplateIntent);
        ArgumentCaptor<AACSMessage> aacsMessageArgumentCaptor = ArgumentCaptor.forClass(AACSMessage.class);
        Mockito.verify(mWeatherDirectiveHandler, Mockito.times(1))
                .clearTemplate();
        Mockito.verify(mWeatherDirectiveHandler, Mockito.times(1))
                .renderWeatherTemplate(aacsMessageArgumentCaptor.capture());
        AACSMessage aacsMessage = aacsMessageArgumentCaptor.getValue();
        assertEquals(aacsMessage.topic, Topic.TEMPLATE_RUNTIME);
        assertEquals(aacsMessage.action, Action.TemplateRuntime.RENDER_TEMPLATE);

        Optional<String> sampleAACSWeatherMessage =
                TestResourceFileReader.readFileContent("aacs/RenderTemplateWeather.json");
        JSONObject sampleMessageJsonObject = new JSONObject(sampleAACSWeatherMessage.get());
        assertEquals(sampleMessageJsonObject.getString("payload"),aacsMessage.payload);
    }


    private Intent generateIntent(String resPath, String action) {
        Intent intent = new Intent(action);
        Optional<String> sampleAACSMessage = TestResourceFileReader.readFileContent(resPath);
        Bundle sampleListeningPayload = new Bundle();
        sampleListeningPayload.putString("message", sampleAACSMessage.get());
        intent.putExtra("payload", sampleListeningPayload);
        return intent;
    }

}