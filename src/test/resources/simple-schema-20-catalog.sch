<!-- Simple XSLT 2.0 Schematron requires a catalog resolver -->
<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <include href="http://example.com#title"/>
  <pattern id="always-valid">
    <rule context="*">
      <assert test="true()"/>
    </rule>
  </pattern>
</schema>
