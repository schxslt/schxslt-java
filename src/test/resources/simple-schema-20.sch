<!-- Simple XSLT 2.0 Schematron -->
<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <pattern id="always-valid">
    <rule context="*">
      <assert test="true()"/>
    </rule>
  </pattern>
</schema>
