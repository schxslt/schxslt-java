<!-- Simple XSLT 1.0 Schematron -->
<schema xmlns="http://purl.oclc.org/dsdl/schematron">
  <let name="external-param" value="2"/>
  <phase id="external-param">
    <active pattern="external-param"/>
  </phase>
  <phase id="always-valid">
    <active pattern="always-valid"/>
  </phase>
  <pattern id="always-valid">
    <rule context="*">
      <assert test="true()"/>
    </rule>
  </pattern>
  <pattern id="external-param">
    <rule context="/">
      <assert test="$external-param = 1"/>
    </rule>
  </pattern>
</schema>
