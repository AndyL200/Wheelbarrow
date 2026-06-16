import base64

image_path = "com/src/Assets/setting.png"


try:
    with open(image_path, 'rb') as file:
        file_bytes = file.read()

        base64_bytes = base64.b64encode(file_bytes)

    with open(image_path+"to_txt.txt", 'wb') as file:
        file.write(base64_bytes)

except Exception as e:
    print(f"An error occurred: {e}")

    