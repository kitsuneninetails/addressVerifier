
from python_utils.net import curl_utils


address_map = {
    "": 0
}

score = curl_utils.curl_post("http://localhost:8080/address_check",
                             json_data={"adress:" ""})

