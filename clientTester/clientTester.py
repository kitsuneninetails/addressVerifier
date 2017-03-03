
from python_utils.net import curl_utils

address_map = {
    "addr1": {
        "line1": "A",
        "line2": "B",
        "city": "C",
        "state": "DE",
        "zipCode": "11111"},
    "addr2": {
        "line1": "A",
        "line2": "B",
        "city": "C",
        "state": "DE",
        "zipCode": "22222"},

}

for i in range(0, 10):
    for name, addr in address_map.items():
        score = curl_utils.curl_post("http://localhost:8080/addressScore",
                                     json_data=addr)
        print("Score=" + score)

