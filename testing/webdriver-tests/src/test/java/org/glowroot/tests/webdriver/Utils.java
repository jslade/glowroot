/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.tests.webdriver;

import java.util.List;

import com.google.common.base.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Utils {

    public static WebElement withWait(WebDriver driver, By by) {
        return withWait(driver, driver, by);
    }

    public static WebElement withWait(WebDriver driver, final SearchContext context, final By by) {
        return new WebDriverWait(driver, 30).until(new Function<WebDriver, WebElement>() {
            @Override
            public WebElement apply(WebDriver driver) {
                List<WebElement> elements = context.findElements(by);
                if (elements.isEmpty()) {
                    return null;
                }
                WebElement element = elements.get(0);
                if (!element.isDisplayed()) {
                    return null;
                }
                List<WebElement> overlayElements =
                        driver.findElements(By.className("gt-panel-overlay"));
                for (WebElement overlayElement : overlayElements) {
                    if (overlayElement.isDisplayed()) {
                        return null;
                    }
                }
                return element;
            }
        });
    }

    // WebElement.clear() does not trigger events (e.g. required validation), so need to use
    // sendKeys instead
    public static void clearInput(WebElement element) {
        // select text (control-a) then hit backspace key
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
    }
}
