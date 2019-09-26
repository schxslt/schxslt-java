<!-- Simple XSLT 1.0 Schematron -->
<schema xmlns="http://purl.oclc.org/dsdl/schematron">
  <pattern id="always-valid">
    <rule context="*">
      <assert test="true()"/>
    </rule>
  </pattern>
</schema>
