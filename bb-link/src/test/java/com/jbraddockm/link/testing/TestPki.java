package com.jbraddockm.link.testing;

/**
 * Throwaway certificates and keys for the tests. They are made up and protect nothing: a CA named
 * like the device's, a client certificate shaped like the one BlackBerry Link holds (RSA 2048,
 * signed with SHA-1, issued by a CA that the server does not list), the same key in the forms an
 * export can have, and a server keystore.
 */
public final class TestPki {

  private TestPki() {}

  /** The client certificate; issuer {@code BB10/000…}, subject in the BBCGIAUTH style. */
  public static final String LEAF_CERTIFICATE =
      """
      -----BEGIN CERTIFICATE-----
      MIIDHjCCAgYCAhz3MA0GCSqGSIb3DQEBBQUAMG8xIDAeBgNVBAoMF1Jlc2VhcmNo
      IEluIE1vdGlvbiBUZXN0MRMwEQYDVQQLDApCbGFja0JlcnJ5MTYwNAYDVQQDDC1C
      QjEwLzAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwHhcN
      MjYxMDE1MTM0NjQwWhcNMjYxMTIxMTM0NjQwWjA6MSgwJgYDVQQDDB9CQkNHSUFV
      VEggW0FBQUEwMDAxXSBURVNUTEFQVE9QMQ4wDAYDVQQFEwUxMjM0NTCCASIwDQYJ
      KoZIhvcNAQEBBQADggEPADCCAQoCggEBAMKL43ONvPV2A6Q23B9+SjKdeGdbjoNz
      bsTyrdFsn4PdgcLzjwRbF25KZf2P/e7cFIUxgJRdtu09aGa4MYoLxmMWQxw3okKq
      sOLkk1ipjlpsJV0YORUmiEJx4Zpt8b4W1Oj9nMuTa0f1e/P/kk8iFFDwWqmvEbal
      BV2XeN+nqujo1hhAec40AE5panpv/8WTY1We7x2572CvEfRousZGMKMCXZLs+t4t
      SmPSjX+Vfn5WJlObEJoTkE9PJzwuG0ExoGZxBdjv+tbkA79RiGk6Urmk8WMnhwiL
      255wnFo1/hxJuO8g2v+krNc9X/DG5ZyTiPuqISHRLm6aIq1jnoXbd6kCAwEAATAN
      BgkqhkiG9w0BAQUFAAOCAQEAGEzWU8LRNkNqI5EeV10/SKqYPtJ83c/xGghhFXQF
      n75N8cvewFQY9driG0eiHQj7baDR5Y+k+KzMWn+B99UTI2GWAiVWqbimxxwiQUjr
      hLg4kWHfGEEOaQWkb7bdlHA+eg1DT29gV8hZTDBdwKrc4vklSVmPmCE56cIKzwxy
      n0F9+Inad5ywBbn60kFQDu4CHg1YpxJI1NLhJ/UmrsPyTl5V7bHX6y/ALJ77ecAf
      oWjQma7wwXrAFB9rOhNgJgmxhrBvKVAsxW7VNy/Y4Co4uz/tf6S3DJSsXrRL0+Jc
      ZrTU+Yb5QSNgH8wXaIw0rRvh6xBNSYdu76DV7phMhVC7YQ==
      -----END CERTIFICATE-----
      """;

  /** Its key as {@code PRIVATE KEY} (PKCS #8). */
  public static final String LEAF_KEY_PKCS8 =
      """
      -----BEGIN PRIVATE KEY-----
      MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDCi+Nzjbz1dgOk
      NtwffkoynXhnW46Dc27E8q3RbJ+D3YHC848EWxduSmX9j/3u3BSFMYCUXbbtPWhm
      uDGKC8ZjFkMcN6JCqrDi5JNYqY5abCVdGDkVJohCceGabfG+FtTo/ZzLk2tH9Xvz
      /5JPIhRQ8FqprxG2pQVdl3jfp6ro6NYYQHnONABOaWp6b//Fk2NVnu8due9grxH0
      aLrGRjCjAl2S7PreLUpj0o1/lX5+ViZTmxCaE5BPTyc8LhtBMaBmcQXY7/rW5AO/
      UYhpOlK5pPFjJ4cIi9uecJxaNf4cSbjvINr/pKzXPV/wxuWck4j7qiEh0S5umiKt
      Y56F23epAgMBAAECggEAB6RDh6kwXCrpyzQ9cr48716ImXS69RZeaj2HDtQXsvAL
      NgtnmXnzgmlJNsvGY3DBHJc8b/d7ZTe1h11iUsQSI5e/BJd51+kJACyOzhuA5DPd
      xtmZ+mb0ULnU7QI3DeqrK8i0dTmh6+ZsW0H1bwbNaf167jHafgRe4NV/kbduCu5y
      yhMkxfL9xHV7lUw3djTOAHRVTXCm3UC8JzohcBB1zT24on2PixnUzk2GPYhQUYUl
      huiGn1fl64T2wPdwvpuXftwCppZet72fSrJ4H/q037jliz1cIZs+R5r8Fta/cFRU
      6WPWwPLle5HL1sSiASydC90Vjm7hHdONuFuBz95vtQKBgQD0J4qAuyTpjiFmIzNM
      9OCd0qPTDQF8TthvB4mLVFznD+qWRV1hO22KkgCwK3yEfZUxDevP48vrvpUbuosx
      RNZz3yRvj+VUROUBeAw/fx66YLOg/jtUhjbyB83WSI5OUgPznPGETNtP4KgJEO2k
      CPvblkFrGz24JpiS2f/4Vtz2xQKBgQDL/DQcax4ItZDh1G9qTTQhcu/MAjSz0Zxr
      fNePsJKbuOFYDRNJ6dDjYhvMd3hiqXfV3dUWzvDGaQJqL7K0epHor5/F4rWLPINR
      tFAEkzVXan4tpiTrYiX8LNLJd7pnm/QNMuJT9blVjs7g0zjFr3m41bIpAkgG59lu
      OMCXAWXrlQKBgDey+Zbqmr4pbZcmeqKYztFSZ4tTUT5xolCaEJfXlCYmbGcBxg7T
      hubw7fVCxnJXT/zT5zMTDcdnQA8GXJDN2nquslo9Ip055mCSaYDm2DS+sNwOg6yB
      vfEzNJMpFEXr0fj2meaLvhCX+N2wnfYpEoQC4EO2ADuNmosN7qZR8rDdAoGADnOQ
      sY65Mv7CA+3L8ukDT3dYG8jwOOkZvte6YditmlCvzlAm1K62+NUDiSnRopPFBKbR
      qOsCTdw0qpc0furMGYMPu7JDEFk6KHiwzpIrTkUiKVPC+9YZQNNgH5vW3e5DjQfU
      1wPqnHAgofb057R12C1zkMozliGo2tE0e75CnT0CgYBojUEwbTMu0WVWi7s9DcYn
      5Ee/ZV2wjZ5c0SZU03jEARy3MiSKi4LN782bsun+x+U+NREu/uBC5NKilkhH5QLk
      chXzmDNJi9yweU1N/0dquSXtkjs1asD4rt8efyGTuMRngHdE5LYLPxb4oDzYL1ch
      1bWef1FsMvAlyO9rF9kTaQ==
      -----END PRIVATE KEY-----
      """;

  /** Its key as {@code RSA PRIVATE KEY} (PKCS #1). */
  public static final String LEAF_KEY_PKCS1 =
      """
      -----BEGIN RSA PRIVATE KEY-----
      MIIEogIBAAKCAQEAwovjc4289XYDpDbcH35KMp14Z1uOg3NuxPKt0Wyfg92BwvOP
      BFsXbkpl/Y/97twUhTGAlF227T1oZrgxigvGYxZDHDeiQqqw4uSTWKmOWmwlXRg5
      FSaIQnHhmm3xvhbU6P2cy5NrR/V78/+STyIUUPBaqa8RtqUFXZd436eq6OjWGEB5
      zjQATmlqem//xZNjVZ7vHbnvYK8R9Gi6xkYwowJdkuz63i1KY9KNf5V+flYmU5sQ
      mhOQT08nPC4bQTGgZnEF2O/61uQDv1GIaTpSuaTxYyeHCIvbnnCcWjX+HEm47yDa
      /6Ss1z1f8MblnJOI+6ohIdEubpoirWOehdt3qQIDAQABAoIBAAekQ4epMFwq6cs0
      PXK+PO9eiJl0uvUWXmo9hw7UF7LwCzYLZ5l584JpSTbLxmNwwRyXPG/3e2U3tYdd
      YlLEEiOXvwSXedfpCQAsjs4bgOQz3cbZmfpm9FC51O0CNw3qqyvItHU5oevmbFtB
      9W8GzWn9eu4x2n4EXuDVf5G3bgrucsoTJMXy/cR1e5VMN3Y0zgB0VU1wpt1AvCc6
      IXAQdc09uKJ9j4sZ1M5Nhj2IUFGFJYbohp9X5euE9sD3cL6bl37cAqaWXre9n0qy
      eB/6tN+45Ys9XCGbPkea/BbWv3BUVOlj1sDy5XuRy9bEogEsnQvdFY5u4R3Tjbhb
      gc/eb7UCgYEA9CeKgLsk6Y4hZiMzTPTgndKj0w0BfE7YbweJi1Rc5w/qlkVdYTtt
      ipIAsCt8hH2VMQ3rz+PL676VG7qLMUTWc98kb4/lVETlAXgMP38eumCzoP47VIY2
      8gfN1kiOTlID85zxhEzbT+CoCRDtpAj725ZBaxs9uCaYktn/+Fbc9sUCgYEAy/w0
      HGseCLWQ4dRvak00IXLvzAI0s9Gca3zXj7CSm7jhWA0TSenQ42IbzHd4Yql31d3V
      Fs7wxmkCai+ytHqR6K+fxeK1izyDUbRQBJM1V2p+LaYk62Il/CzSyXe6Z5v0DTLi
      U/W5VY7O4NM4xa95uNWyKQJIBufZbjjAlwFl65UCgYA3svmW6pq+KW2XJnqimM7R
      UmeLU1E+caJQmhCX15QmJmxnAcYO04bm8O31QsZyV0/80+czEw3HZ0APBlyQzdp6
      rrJaPSKdOeZgkmmA5tg0vrDcDoOsgb3xMzSTKRRF69H49pnmi74Ql/jdsJ32KRKE
      AuBDtgA7jZqLDe6mUfKw3QKBgA5zkLGOuTL+wgPty/LpA093WBvI8DjpGb7XumHY
      rZpQr85QJtSutvjVA4kp0aKTxQSm0ajrAk3cNKqXNH7qzBmDD7uyQxBZOih4sM6S
      K05FIilTwvvWGUDTYB+b1t3uQ40H1NcD6pxwIKH29Oe0ddgtc5DKM5YhqNrRNHu+
      Qp09AoGAaI1BMG0zLtFlVou7PQ3GJ+RHv2VdsI2eXNEmVNN4xAEctzIkiouCze/N
      m7Lp/sflPjURLv7gQuTSopZIR+UC5HIV85gzSYvcsHlNTf9Harkl7ZI7NWrA+K7f
      Hn8hk7jEZ4B3ROS2Cz8W+KA82C9XIdW1nn9RbDLwJcjvaxfZE2k=
      -----END RSA PRIVATE KEY-----
      """;

  /** Its key, encrypted with the password {@code secret}. */
  public static final String LEAF_KEY_ENCRYPTED =
      """
      -----BEGIN ENCRYPTED PRIVATE KEY-----
      MIIFLTBXBgkqhkiG9w0BBQ0wSjApBgkqhkiG9w0BBQwwHAQIu677MExUhMACAggA
      MAwGCCqGSIb3DQIJBQAwHQYJYIZIAWUDBAEqBBAUiceBedTpwFEIDGHAZZQHBIIE
      0PFSXIyy92IQlQ93eJMPeFDd9ZAwZMKPsquE87kDzijltY2kzu7WkmZhh9Kkj2Wn
      E3sVFwuf+gay324FTHqv52u17zf311r4H9fI14n26/m6gh11nfojIFNQQ6dlCKEA
      VMeEjiYEo8YMEJAOGbMnBalI9ieaW3oC2p6tDNr6H5rcEHcVCoZfCgqE4gqR5EEC
      FI99IJOkkePESI9jkeHwl77LfuhmmkThVq2iRzrQ5qMN9rKkI3GkP4RqG45mjmJk
      Cf5C6VgYLYjujNh5d9GTPqv3jhKNdg3LVJkZ2aYemGjg35DtbzWileMyprHfN3Bq
      /wemPoX1itxqlWAmhCrmZYy6BGzsrbawOFQ7F604/XCBHnTu3k5W0diFbVC4hIR+
      gP3fUHqVthx+c+12jR3G3y+VNeD2W2JKYFhTxskjyozaxFTJoY1ABf4+m0ghy8QP
      NdwWcNxeS4W4YB0nUfjHT+LTl+e/y7RQkcKQPM8px5UC7Nof6l0fB47A0KziuefX
      KK1XyH41dc83OndP9/KV8Uut4qDq/4cJG1jidyOO98ArZsR3SygWdBOSDYZ7VLqo
      h3q/xWfR1ZKZvuUUkNv/nvMtyoMeXuYKiH5ftqUJ6Sx/3uilmXjt1IDsArYUgR4D
      XtFJcBxS2MHziqG1COgTic29SvXKD45l0w8OvcpJUpsX1exLX+oAibT9gLcaN0/N
      gDfZAFdPjP3Qxbzc5i4iEXusWPlA/ZjZaeP21SDGgYQ4fhGIZxqMukD20SmTqkXg
      R0U2YyiyWDYbQhRciMr7HmTSuDMD3lAHghEYdbVks4iLetmw8+C82TjKPRQnxr73
      mwBrzGfiSnV4Aklbhw7cvgnZOx+zXg7F3wSaGZeZm6SNRChqzMtbst/BrwFjZ5uP
      v5b/LjD2J+UFgT6D+sHEbaq1ETz/pp1xNlSgdH2O8cCwEWy13vYOoZ5G7zQoe+LP
      XnAgHrnYu8x00AhXk3MTj8GrXGoKxF/ndMzYN+R1biR5aRABqEXjISI7Ch7yLA5Y
      GwiJ5UOB5cXn80oCvHs1g5uWDDDU91DNEU880IHrDBQeWvA0OB6L0W1XkJi36hXP
      z9ac8qoiy+xB6NqGxQ1DxboJDxWidfTZRKeewnpST808eCLfuxsCB6Hgz6aN9J9z
      3CydLixJ3vrKldrfkemDpgop9Y/y3iC5WKBWCjB0dP7qr/+EkKHuNrnGMI2v0mCg
      JoCQf1+9M4167GNZiXJJ7xwYjAgpAKkuj3MoqCRUHVCBYN64ec+k8M4bLXvW8QeY
      LsfqcTOU+jwZiok6Uu79gLFymDw98vmcZNwrKvFM/x3AwmZnscFi7y3jbdbcO/b1
      ruYdYXqvCknoRcqNOTHI+ls77N0AeutWQI0d7f/eTT+BbHtkfnvWJAZj207Vl+sO
      JNISbk8WwrDBpL/LyOPNTS2t5n19vqHFqYjYKh6mVJb3puu1fZx8fOKaIHveJj4A
      61wuzTOw3Y5aDiAAORSI7dD1BuX6qxoBg+WAT+XxUTHO1PxgRp4HpHrLMbWlCKp1
      y38nvfwL6RKkDq+dOXVoFKwi7tvk7gvc2HWIuk2TZW5tHES9ZInS823dcCIqx2mv
      i3rPyKdLebbpEgKsUR9WevKERzqph0bU66PW5PFQf5Av
      -----END ENCRYPTED PRIVATE KEY-----
      """;

  /** An EC key in the older SEC1 form. */
  public static final String EC_KEY_SEC1 =
      """
      -----BEGIN EC PRIVATE KEY-----
      MHcCAQEEIOG4vO3lfnd37OFwCO/Ic1S3SHKmioM+i4w3TrNuZgGnoAoGCCqGSM49
      AwEHoUQDQgAEz7WS2moQq/xU0BZ+HgyPDRwhS+73PrEqwYqdfOD4RvIdHO2Gm374
      ACO7ADw2EWjcCNKlVzmyj/rT3SyfEidfDA==
      -----END EC PRIVATE KEY-----
      """;

  /** An RSA key that belongs to no certificate here. */
  public static final String OTHER_KEY_PKCS8 =
      """
      -----BEGIN PRIVATE KEY-----
      MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCQ7a9EoURfeNWy
      bwMME6gm4mQaLk2jJ23MURt66VCFYJr8LdXxoJul3PQAnu2C7G5FY6cVYWQAiXRI
      vXrk6jRyb9ihIdniXMgGf+fuBeCDA495vg423FV6Btf8PlVDDmEMsmRQJ3CF+/Xm
      5Yg0o84vQ5yUHhJrWLTaobbIy967KTT4xzPQPy/BbsVp33xPBBCe+9D7uS/3xsyx
      7sfqpKfzpOG3H/k2MVi65xJtVruUOpGwhzu0b9hRGK4M88fM5IL/KlGYvOV5YTNG
      fQS/B9B/iTR3ArXgq1AfA4CPf8yFnAjxe7g7aT8eLg6m9Px/VnD/KKGMo2oWrSrA
      HNUcxzFJAgMBAAECggEAAkpbW4KP9dFPUUlbWzjBTtIDon1KWVKBc/nxLP1ceisn
      b+Zai/3Nj+7AxXS9jLTnXj3fsWEPZ0nE1nR2KYGBEDDv08YY7zCnsaCaXgLmRDPN
      c+9awqezSyEqDzC+ALgMtRpppvGkn912Sq6key5+jvUq15VtFZi6jP0CykQckkkj
      mDyVfNTWR++jwsu5QhbuZFP1tksQg02LsA5wiW+R4u4ewCby2YCnJ1TF+IhpOBsF
      d98KnyFMSrv1kTBEDrF+dUkiU6OEY8EFsYmr4kfcQPFU1Z1gOON7SnnMfg008w3j
      LZTzTyeuY57E2mJMvflFqDGpi0iaJjoxHr5aX+tiAQKBgQDCamQN19pvOS6hIqvi
      m5pNKmaPRIiN/ohztsZ6mkpix1tOYuG5AwOYP074JNfS2sCDAiLUONqet0QgEQ+9
      JAsInUR+irFXco3ZYyz6mbx5ny7yueeW7IV3411f+WrxhvPWrUzSeJLqgQLu0QZA
      md6E4Y0Mh68md82xrI6xTv+SSQKBgQC+1kSRchpzmMSySivc+b8KJFpr+tgyKiri
      v+4iNu4ePqNAVpqQ6TbbvI30wx5EeuKHE5p5HNC/vbsFssWElU+Vv5yi2i3nTzRg
      PNHCG/ccGZydFD2zzfjwpqYSyDTc6FQ6D0IkNKiYI4ZVnijJTJgVRt3y1Ng6RUDk
      q6EkaoqnAQKBgCAkXMRZInju+EGVrNs7JIiLcQdQqk7nfQG8fxX3+kJYeUT9Xhor
      YGXEWbb2yGPI3LkMGlwBxkslO3Mdvv9jPRvAfHl7TpSJkOAW4HHSBvIImkdw6tPt
      6KcIhQCACOlDiX69igzl+Ewi5iw7jJAToFFRxPMEX7W5at1sDO9Ngz1ZAoGAGHo3
      NUijZ+xSXmPxGFjYTh63kl4MWPLbqVXg33aYODzkh54UGT4wkSKwOpLEURhJ8fVr
      vNkbVVyqnmJ9vzG/kX+QQbsmZCLenvFuHi2kFnCai/NKrsNeiM93QbV4QlVyaDgI
      zcgLT+hOlLDCnn86ipAaa8ktKtwzJKF2+8rGmgECgYB6iHp41zYdlwh7Rh0xIVR+
      l+a/6R0rWE6B0tQ8lgJQNifVvgXk41d/GNXMB5Rqjbp/vAxHLWY7CPt7EBmJmvVr
      98u2RYziX8pheQvj5Kvccg3glXwVTN3DSzDMQ/eWMScaBRKphyIlMbLCopZ+PPf5
      3U7PNP7m91fyahBZC3RTIA==
      -----END PRIVATE KEY-----
      """;

  /** The server keystore (PKCS #12, password {@code changeit}) as Base64. */
  public static final String SERVER_KEYSTORE_BASE64 =
      "MIIKHAIBAzCCCdIGCSqGSIb3DQEHAaCCCcMEggm/MIIJuzCCBBIGCSqGSIb3DQEHBqCCBAMwggP/AgEAMIID+AYJKoZIhvcNAQcB"
          + "MFcGCSqGSIb3DQEFDTBKMCkGCSqGSIb3DQEFDDAcBAh0ddZcfZs/iAICCAAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEENUs"
          + "AWGfeejdWpuqZjfFOI6AggOQ4vHt9+lhU8yd2oYeFxV6eMcIo+PntyEV9aZAO1XQXCjN3LVBGgA6CLbBLH5Recx0kVvWIe4JqnyR"
          + "i43RIAVU846rSW00HRLZ4EQpgwUs7n2kHD9wMfn21RzuTg3MWb1HO42Xh4FtBAFaNpqZO1taz4vnnZIf1biId++/jAK3k63EqBmG"
          + "R8SyvmEY1klAlLqG2tTGSwRerS2Crweih8oDQw+HFQ3s/s+0fbm4KPEXJRU/b0RrK89A0QFdTzFlQARpY75XDtE7wZ4D+d0RQ/te"
          + "wPdVoKs1LRIs/IC7ODQemveLkeXYnUrkWkum1SEe8AAkzxvtwKWHkqD8KjRnY+O758IG4umhSKDnl2UiHTvujfR4GBB2E938BfHE"
          + "0sjUNKjBkYixDr7nIZSmRvZR9ftW7eZMzJu48geDSEjxyWc6PNxCq5ioGpTiIaeUi5JgdgLMexH4a6J6KRJvC2Rfydh8cbA2pCSM"
          + "RJKKoB0jv1SNbVQMO1YRvAxQU8RTXG+4k1eL4ouD9J67K/I8OU+kKrdykSOgEBZYQ0e9ajPsyJ8WPD7qYLwgY7FHPEM5lk0TAyvF"
          + "9YoJU8OOF+zT/ZMOQ9U0bGsFBUYAidaK9wXYOJ9EeJj9LAfS0kGpfowUDS00T05mqDFLy2em9LzvDyp6BeucMXVbw8/34Qk1PtTj"
          + "oIoPcMd8BSvvMXpaYBlOOxSzb7fp5R54An+rGAOqGm6EM57LIM3llqvSJCAFmofpjuaeqWdy45u4yK7uN651OXA33gy1aLI0eILl"
          + "Oebl8dbaDEIrAn0hDeaXyS/XyKE2ApDt4z6Htw8OBN/61uCeN78dgjmRqVQhlwE4vb/HXk0Up1SFUcv/u8XNPCF+IbUKhWHx19u4"
          + "P92KeVX7fpwzXQBjCMPwznwjZl/68tVEaRf8MG6Sn4EBIGIIPnvetxFGcBnvt1zRW0nrPBPodpr7I2ETxq2GeqHI16HIRvdiaoPL"
          + "pskrUIqSa+AqqgugCq+GmBGRj0pGXQfttCAmkOVbVY9tz7OPdyJwoTOWVYqm4GlzLIjARIsFG1CDA6PcCyKeS23VUkQoVr2Zi0re"
          + "dDCH2wDLwfGP62WI3UlXV3t+ExzdsJjuuzglDkuo6XQdJR6B8TvUjLDrPsSNNuZqZJjWrupFliTS5B9Jg7Q/9aoUno6Obq6hDFxM"
          + "5UxXLwOOtDNacU3QnnKE4QLDUJ5OXxzJHpcWQoR5MIIFoQYJKoZIhvcNAQcBoIIFkgSCBY4wggWKMIIFhgYLKoZIhvcNAQwKAQKg"
          + "ggUxMIIFLTBXBgkqhkiG9w0BBQ0wSjApBgkqhkiG9w0BBQwwHAQIZQlaqD0OCd8CAggAMAwGCCqGSIb3DQIJBQAwHQYJYIZIAWUD"
          + "BAEqBBAzaH1yC8WcZ0NCN8qBWlASBIIE0B3ksTypopmtw/HlnVzkiF/e2gLTI0QovFFrl1bmjhxW86vpNCHPeheG+DGj5BgvNakd"
          + "x7KmhJWHmIV2DKB3eVB9rXHU3IJaaTC5SjYAvmk6eKnLvlspUVEcZc5N7KKJwHMGfqsYsPVLpi49dj+I6ecT0EymcXNz5b5l0hLP"
          + "MdrpfC60/GOgO06NAaYA5qlZzGgcVzrK88g7S58K58CWuF/4ghPy+5xQFuUdv1Bci8LxqbSa76FDHyTM7Ftc3FsF9ZahvZSp4kmb"
          + "4lEo/umxDrRGhp8GXsz5kyIYLcmSG092EflE0iUsps/0Sr7BhLSK914zVgLxB9+uaphF+ODQPzdX3/VvnQYNTViN1OaW4A0Uw3oK"
          + "5/vvq7ZA7IWLTESOObfpaNXEWOdObMmI6VMgeb6QP4IWZdVS574YjGDBieHaG39YBdNv4uR1WlQy5q6fSrPydXPEuFS/+gAAafFv"
          + "+/x2XT24zdr5UhSU+B1LamO5XMuv1E2JZk2+MHfre3mj5TTBkstEiGjGL/7VKY8QHBTY3NBWnWNuXiNPCQtDEuTqvAZ1loFivavr"
          + "Ogltbc6s76cDB62rA7eJxTGW7U4QwYc1bz+LGkXIDJ/DcvU+5Sc1+q7Br794ChN2tUs1aCaqRct7mmzLhGLpTrDDmfodTwAONTdT"
          + "ymLQRJ1SLnGW+X6NXxxLrN816Bu8k4FzondYY9/eoVl3g9jZziVRdxuYKEmZ678xKRuGHx3k5pa/hcnoRFMfaNBt5BqK63DXw4GV"
          + "w43RfffWM+dYNM4j2xCQb2eIFSCS4Ep54xy6nbhd+pcVeouuPlSgKW0NvwpXZuInvn1479wPLZLtw+DwsLn5AcJKx9vSVpUDCGF/"
          + "dreUmYdRAhQw6LSytvX1r2vpzRDd4kv0w9ri/QHzGH9eKsnh5+kUBosx3yw1hWPpv5JV7HfqiDcG7boNHtmpsNW5ZlqHFp3nm6Vw"
          + "8BbWqoBUrRTVwRlrVcrAKFNzDKQZSg2eNBNe1qMDjK8E4/FpfMQSIwqUv0Q9mIV4wft48XsLbVWjSKQ9dc0iT9kgaN1nKcYCriA2"
          + "06rQXExMS024MCbPu4Y+2HKmF/NKN6vV+dG188jbF8M62IET+Cfk5SoHcH8Lltt22KlcgAZZPxvP7pu53BlcFQqScGldVfJ+BxOD"
          + "cefv2M44RXXJeNChnxvXr/GY7+d/gVNe7ZwgKmMmKG1XEB4HprxIXz9h10o2hrUpQG5CjTh43xWAXJyizrFYqWNg1o7lz1uLiErB"
          + "JVxA4klKdaWN9bcZ8vq1CgpFM/omM56bnuPWqKs5uLl+dMJXTcc4XYv0AskWvth9CNky0JcUgbLZxi8uQ01ZJ9GAGEt0TK+9XCg3"
          + "J7FlLIWnFOFEN7EHfUBXkgyNBcj+bwQdfnQX3BwoCh69sr6OAaPEfdY0U13lKW6pbCFEafissrPAxsau9KuZ0fcTkYoRiLSgSC/Z"
          + "ShwND3zm89vby+3kNP+guTlBBugdytp/68TqpLpk6OYKQGA0xu0rKUzfzvEvEW6jINGaZVB+n8YIKB9SQjI4eCfDXsjMfAXkorBj"
          + "iNA0CdpeFD++9759YQS6P2WCam1bwVcU2AVF8GhG0JyLN/9wQ+KVHeC0g+QeLrcsLKCjR/MjLcw+MUIwGwYJKoZIhvcNAQkUMQ4e"
          + "DABzAGUAcgB2AGUAcjAjBgkqhkiG9w0BCRUxFgQUPzLU2w07+qlBkRsNHUCYZ8GfJsYwQTAxMA0GCWCGSAFlAwQCAQUABCB7IJqE"
          + "exo6AtQVnLLEwRj1q5A3tWEYkeELdkx93N6VTgQIHdzjELsW5o8CAggA";

  /** The password of the server keystore. */
  public static final String SERVER_KEYSTORE_PASSWORD = "changeit";
}
