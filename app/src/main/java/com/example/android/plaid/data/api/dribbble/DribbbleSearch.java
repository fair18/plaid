/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.plaid.data.api.dribbble;

import android.support.annotation.StringDef;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.example.android.plaid.data.api.dribbble.model.Images;
import com.example.android.plaid.data.api.dribbble.model.Shot;
import com.example.android.plaid.data.api.dribbble.model.User;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dribbble API does not have a search endpoint so we have to do gross things :(
 */
public class DribbbleSearch {

    public static final String SORT_POPULAR = "s=";
    public static final String SORT_RECENT = "s=latest";
    private static final String SEARCH_ENDPOINT = "/search?q=";
    private static final String HOST = "https://dribbble.com";
    private static final Pattern PATTERN_PLAYER_ID = Pattern.compile("users/(\\d+?)/", Pattern
            .DOTALL);

    @WorkerThread
    public static List<Shot> search(String query, @SortOrder String sort, int page) {

        // e.g https://dribbble.com/search?q=material+design&page=7&per_page=12
        String html = downloadPage(HOST + SEARCH_ENDPOINT + URLEncoder.encode(query) + "&" + sort
                + "&page=" + page + "&per_page=12");
        if (html == null) return null;
        Elements shotElements = Jsoup.parse(html, HOST).select("li[id^=screenshot]");
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy");
        List<Shot> shots = new ArrayList<>(shotElements.size());
        for (Element element : shotElements) {
            Shot shot = parseShot(element, dateFormat);
            if (shot != null) {
                shots.add(shot);
            }
        }
        return shots;
    }

    private static Shot parseShot(Element element, SimpleDateFormat dateFormat) {
        Element descriptionBlock = element.select("a.dribbble-over").first();
        // API responses wrap description in a <p> tag. Do the same for consistent display.
        String description = descriptionBlock.select("span.comment").text().trim();
        if (!TextUtils.isEmpty(description)) {
            description = "<p>" + description + "</p>";
        }
        String imgUrl = element.select("img").first().attr("src");
        if (imgUrl.contains("_teaser.")) {
            imgUrl = imgUrl.replace("_teaser.", ".");
        }
        Date createdAt = null;
        try {
            createdAt
                    = dateFormat.parse(descriptionBlock.select("em.timestamp").first().text());
        } catch (ParseException e) { }

        return new Shot.Builder()
                .setId(Long.parseLong(element.id().replace("screenshot-", "")))
                .setHtmlUrl(HOST + element.select("a.dribbble-link").first().attr("href"))
                .setTitle(descriptionBlock.select("strong").first().text())
                .setDescription(description)
                .setImages(new Images(null, imgUrl, null))
                .setCreatedAt(createdAt)
                .setLikesCount(Long.parseLong(element.select("li.fav").first().child(0).text().replaceAll(",", "")))
                .setCommentsCount(Long.parseLong(element.select("li.cmnt").first().child(0).text().replaceAll(",", "")))
                .setViewsCount(Long.parseLong(element.select("li.views").first().child(0)
                        .text().replaceAll(",", "")))
                .setUser(parsePlayer(element.select("h2").first()))
                .build();
    }

    private static User parsePlayer(Element element) {
        Element userBlock = element.select("a.url").first();
        String avatarUrl = userBlock.select("img.photo").first().attr("src");
        if (avatarUrl.contains("/mini/")) {
            avatarUrl = avatarUrl.replace("/mini/", "/normal/");
        }
        Matcher matchId = PATTERN_PLAYER_ID.matcher(avatarUrl);
        Long id = -1l;
        if (matchId.find()) {
            id = Long.parseLong(matchId.group(1));
        }
        return new User.Builder()
                .setId(id)
                .setName(userBlock.attr("title"))
                .setUsername(userBlock.attr("href").substring(1))
                .setHtmlUrl(HOST + userBlock.attr("href"))
                .setAvatarUrl(avatarUrl)
                .setPro(element.select("span.badge-pro").size() > 0)
                .build();
    }

    private static String downloadPage(String url) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException ioe) {
            return null;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
            SORT_POPULAR,
            SORT_RECENT
    })
    public @interface SortOrder {}

}
