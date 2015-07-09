#!/bin/bash
# 
# July 8, 2015
# CURL adfs authentication script for ADFS resources.
#
# Mark Bland (mbland@iastate.edu)
# Barry Britt (bbritt@iastate.edu)
#

#=====
# Write Debugging information
#=====
DEBUG=0

#=====
# Authentication Information
#  USER: ISU Net-ID
#  PASS: Net-ID Password
#=====
USER=""
PASS=""

#=====
# Application information
#  APPURI: application ID?
#  URL: resource to which you are connecting
#  AUTH_URL: don't change unless you know what you're doing
#=====
APPURI=https://server.com/application
URL=${APPURI}/path/to/data/I/want
AUTH_URL=https://server.com/adfs/services/trust/13/UsernameMixed

#=====
# UNIX binary paths. Should not need to be changed
#=====
CURL=/usr/bin/curl
RM=/usr/bin/rm
ECHO=/usr/bin/echo
MKDIR=/usr/bin/mkdir
CAT=/usr/bin/cat
AWK=/usr/bin/awk

#=====
# Temporary directories
#=====
TMPDIR=./tmp


#=====
# Request Envelope
#=====
REQUEST=`${CAT} << EOF
<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://www.w3.org/2005/08/addressing" xmlns:u="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
  <s:Header>
    <a:Action s:mustUnderstand="1">http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue</a:Action>
    <a:To s:mustUnderstand="1">${AUTH_URL}</a:To>
    <o:Security s:mustUnderstand="1" xmlns:o="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" >
      <o:UsernameToken u:Id="uuid-6a13a244-dac6-42c1-84c5-cbb345b0c4c4-1">
        <o:Username>${USER}</o:Username>
        <o:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">${PASS}</o:Password>
      </o:UsernameToken>
    </o:Security>
  </s:Header>
  <s:Body>
    <trust:RequestSecurityToken xmlns:trust="http://docs.oasis-open.org/ws-sx/ws-trust/200512">
      <wsp:AppliesTo xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy">
        <a:EndpointReference>
          <a:Address>${APPURI}</a:Address>
        </a:EndpointReference>
      </wsp:AppliesTo>
      <trust:KeyType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Bearer</trust:KeyType>
      <trust:RequestType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue</trust:RequestType>
      <trust:TokenType>urn:oasis:names:tc:SAML:2.0:assertion</trust:TokenType>
    </trust:RequestSecurityToken>
  </s:Body>
</s:Envelope>
EOF
`

${ECHO} ${REQUEST} > ${TMPDIR}/tmp.file
${CURL} ${AUTH_URL} -s --data @${TMPDIR}/tmp.file -H "Content-Type:application/soap+xml" -o ${TMPDIR}/output-tmp.txt

TOKEN=`${CAT} ${TMPDIR}/output-tmp.txt | ${AWK} -F'<trust:RequestedSecurityToken>' '{print $2}' | ${AWK} -F'</trust:RequestedSecurityToken>' '{print $1}'`
CREATED=`${CAT} ${TMPDIR}/output-tmp.txt | ${AWK} -F'<u:Created>' '{print $2}' | ${AWK} -F'</u:Created>' '{print $1}'`
EXPIRES=`${CAT} ${TMPDIR}/output-tmp.txt | ${AWK} -F'<u:Expires>' '{print $2}' | ${AWK} -F'</u:Expires>' '{print $1}'`

XML=`${CAT} << EOF
<trust:RequestSecurityTokenResponseCollection xmlns:trust="http://docs.oasis-open.org/ws-sx/ws-trust/200512">
  <trust:RequestSecurityTokenResponse Context="rm=0&amp;id=passive&amp;ru=%2fWebHost">
    <trust:Lifetime>
      <wsu:Created xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">${CREATED}</wsu:Created>
      <wsu:Expires xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">${EXPIRES}</wsu:Expires>
    </trust:Lifetime>
    <wsp:AppliesTo xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy">
      <wsa:EndpointReference xmlns:wsa="http://www.w3.org/2005/08/addressing">
        <wsa:Address>${URL}</wsa:Address>
      </wsa:EndpointReference>
    </wsp:AppliesTo>
    <trust:RequestedSecurityToken>${TOKEN}</trust:RequestedSecurityToken>
    <trust:TokenType>urn:ietf:params:oauth:token-type:jwt</trust:TokenType>
    <trust:RequestType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue</trust:RequestType>
    <trust:KeyType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Bearer</trust:KeyType>
  </trust:RequestSecurityTokenResponse>
</trust:RequestSecurityTokenResponseCollection>
EOF
`

${CURL} -L -s -c ${TMPDIR}/mycookie.txt --data-urlencode "wresult=${XML}" -d "wa=wsignin1.0&wctx=rm=0&id=passive&ru=${APPURI}" -H "Content-Type:application/x-www-form-urlencoded" ${URL}

if [ ${DEBUG} -eq 0 ]
then
    ${RM} -rf ${TMPDIR}
fi
