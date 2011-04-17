package com.twodegrees

import groovyx.net.http.HTTPBuilder
import grails.converters.JSON
import org.springframework.web.context.request.RequestContextHolder
import static groovyx.net.http.ContentType.URLENC
import static groovyx.net.http.ContentType.TEXT

class FacebookService {

  static transactional = false
  def grailsApplication

  def post(service, fbParams) {
    updateAccess(fbParams)
    def http = new HTTPBuilder("https://graph.facebook.com/${service}")
    def j = http.post(body: fbParams, requestContentType: URLENC) { resp, json ->
      assert resp.statusLine.statusCode == 200
      return json
    }
  }

  private baseGet(service, queryParams, baseUrl) {
    updateAccess(queryParams)
    def url = "${baseUrl}/${service}"
    log.info "${url}?${queryParams.collect {k,v->"$k=$v"}.join("&")}"
    def http = new HTTPBuilder(url)
    def j = http.get([query: queryParams, contentType : groovyx.net.http.ContentType.JSON, requestContentType: URLENC]) { resp, json ->
      return json
    }

  }

  private baseGetText(service, queryParams, baseUrl) {
    updateAccess(queryParams)
    def url = "${baseUrl}/${service}"
    log.info "${url}?${queryParams.collect {k,v->"$k=$v"}.join("&")}"
    def http = new HTTPBuilder(url)
    http.get( path : url, contentType : TEXT, query: queryParams, requestContentType: URLENC ) { resp, reader ->
      return reader.text
    }
  }

  def get(service, queryParams) {
    baseGet(service, queryParams, "https://graph.facebook.com")
  }

  def getApi(service, queryParams) {
    getApi(service, queryParams, true)
  }
  def getApi(service, queryParams, isJson) {
    queryParams['format'] = 'json'
    if (isJson) baseGet(service, queryParams, "https://api.facebook.com")
    else baseGetText(service, queryParams, "https://api.facebook.com")
  }

  def getAuth() {
    def http = new HTTPBuilder("https://graph.facebook.com/oauth/access_token")
    def html = http.get(query: [client_id: grailsApplication.config.facebook.applicationId,
            client_secret: grailsApplication.config.facebook.applicationSecret,
            grant_type: 'client_credentials',
            format: 'json'])
    return html.getText().split("=", 2)[1]
  }

  private updateAccess(params) {
    if (params.access_token) return

    def fb = getFacebookData()

    if (fb) {
      params.access_token = fb.oauth_token
    }else{
      params.access_token = getAuth()
    }

  }

  private def getFacebookData() {
    if (!RequestContextHolder.getRequestAttributes()) return
    def session = RequestContextHolder.currentRequestAttributes().getSession()
    return session?.facebook
  }

  def getProfile() {
    return get("me", [:])
  }

  def getProfile(uid) {
    return get(uid, [:])
  }

  def getFriends() {
    return get("me/friends", [:])
  }

  def getFriends(uid) {
    return get("${uid}/friends", [:])
  }

  def parseSignedRequest(srequest) {
    try {
      def r = srequest.tokenize('.')
      def sig = base64UrlDecode(r[0])
      def data = JSON.parse(new String(base64UrlDecode(r[1])))

      if (!'HMAC-SHA256'.equalsIgnoreCase(data['algorithm'])) {
        //@todo: create log messages
        log.error "Unknown algorithm. Expected HMAC-SHA256"
        return null
      }
      def fbArr = [:]
      data.each{k,v->
        fbArr[k] = v
      }

      return fbArr
    } catch (e) {
      log.error("Facebook Communication Error", e)
    }
    //@todo: create proper security
    // check sig
//  $expected_sig = hash_hmac('sha256', $payload, $secret, $raw = true);
//  if ($sig !== $expected_sig) {
//    error_log('Bad Signed JSON signature!');
//    return null;
//  }

  }

  def base64UrlDecode(input) {
    input.replace('-', '+').replace('_', '/').decodeBase64()
  }

}
