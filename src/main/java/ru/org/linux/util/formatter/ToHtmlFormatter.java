/*
 * Copyright 1998-2010 Linux.org.ru
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ru.org.linux.util.formatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.org.linux.site.MessageNotFoundException;
import ru.org.linux.spring.Configuration;
import ru.org.linux.spring.dao.MessageDao;
import ru.org.linux.util.LorURI;
import ru.org.linux.util.StringUtil;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Форматирует сообщение в html
 * Основная функция преобразование того, что похоже на ссылку в html ссылку
 */
@Service
public class ToHtmlFormatter {

  private static final String URL_REGEX = "(?:(?:(?:(?:https?://)|(?:ftp://)|(?:www\\.))|(?:ftp\\.))[a-z0-9.-]+(?:\\.[a-z]+)?(?::[0-9]+)?" +
    "(?:/(?:([\\w=?+/\\[\\]~%;,._@#'!\\p{L}:-]|(\\([^\\)]*\\)))*([\\p{L}:'" +
    "\\w=?+/~@%#-]|(?:&[\\w:$_.+!*'#%(),@\\p{L}=;/-]+)+|(\\([^\\)]*\\))))?)?)" +
    "|(?:mailto: ?[a-z0-9+.]+@[a-z0-9.-]+.[a-z]+)|(?:news:([\\w+]\\.?)+)";

  private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final int MAX_LENGTH = 80;


  private Configuration configuration;
  private MessageDao messageDao;

  @Autowired
  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  @Autowired
  public void setMessageDao(MessageDao messageDao) {
    this.messageDao = messageDao;
  }

  /**
   * Форматирует текст
   * @param text текст
   * @param secure флаг https
   * @return отфарматированный текст
   */
  public String format(String text, boolean secure) {
    String escapedText = StringUtil.escapeHtml(text);

    StringTokenizer st = new StringTokenizer(escapedText, " \n", true);
    StringBuilder sb = new StringBuilder();

    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      String formattedToken;
      try {
        formattedToken = formatURL(token, secure);
      } catch (Exception e) {
        formattedToken = token;
      }
      sb.append(formattedToken);
    }

    return sb.toString();
  }


  protected String formatURL(String line, boolean secure) {
    StringBuilder out = new StringBuilder();
    Matcher m = URL_PATTERN.matcher(line);
    int index = 0;
    while (m.find()) {
      int start = m.start();
      int end = m.end();

      // обработка начальной части до URL
      out.append(line.substring(index, start));

      // возможно это url
      String may_url = line.substring(start, end);
      // href
      String url_href = may_url;
      // body
      String url_body = may_url;

      if (may_url.toLowerCase().startsWith("www.")) {
        url_href = "http://" + may_url;
      } else if (may_url.toLowerCase().startsWith("ftp.")) {
        url_href = "ftp://" + may_url;
      }

      if (url_body.length() > MAX_LENGTH) {
        url_body = may_url.substring(0, MAX_LENGTH - 3) + "...";
      }

      try {
        LorURI uri = new LorURI(configuration.getMainURI(), url_href);

        if(uri.isMessageUrl()) {
          // Ссылка на топик или комментарий
          String url_title;
          try {
            url_title = StringUtil.escapeHtml(messageDao.getById(uri.getMessageId()).getTitle());
          } catch (MessageNotFoundException e) {
            url_title = "Комментарий в несуществующем топике";
          }
          String new_url_href = uri.formatJump(messageDao, secure);
          out.append("<a href=\"").append(new_url_href).append("\" title=\"").append(url_title).append("\">").append(new_url_href).append("</a>");
        } else if(uri.isTrueLorUrl()) {
          // ссылка внутри lorsource исправляем scheme
          String fixed_url_href = uri.fixScheme(secure);
          out.append("<a href=\"").append(fixed_url_href).append("\">").append(fixed_url_href).append("</a>");
        } else {
          // ссылка не из lorsource
          out.append("<a href=\"").append(uri.toString()).append("\">").append(url_body).append("</a>");
        }
      } catch (Exception e) {
        // ссылка не ссылка
        out.append(may_url);
      }
      index = end;
    }

    // обработка последнего фрагмента
    if (index < line.length()) {
      out.append(line.substring(index));
    }

    return out.toString();
  }



}
